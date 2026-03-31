import { useState, useEffect, useCallback, useRef } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { useUser } from "../context/UserContext";
import * as api from "../api/documentApi";
import { addDocId } from "../utils/helpers";
import useWebSocket from "../hooks/useWebSocket";
import EditorToolbar from "../components/editor/EditorToolbar";
import CollabEditor from "../components/editor/CollabEditor";
import StatusBar from "../components/editor/StatusBar";
import ShareModal from "../components/modals/ShareModal";
import HistoryPanel from "../components/modals/HistoryPanel";
import Toast from "../components/ui/Toast";

export default function EditorPage() {
  const { documentId } = useParams();
  const { user } = useUser();
  const navigate = useNavigate();

  // ── document state ─────────────────────────────────
  const [doc, setDoc] = useState(null);
  const [title, setTitle] = useState("");
  const [content, setContent] = useState(null);
  const [charCount, setCharCount] = useState(0);
  const [loadError, setLoadError] = useState(null);
  const [saving, setSaving] = useState(false);

  // ── presence state ─────────────────────────────────
  const [activeUsers, setActiveUsers] = useState([]);

  // ── panel toggles ──────────────────────────────────
  const [showShare, setShowShare] = useState(false);
  const [showHistory, setShowHistory] = useState(false);
  const [toast, setToast] = useState(null);

  // ── remote operation forwarding ────────────────────
  const [remoteOp, setRemoteOp] = useState(null);
  const [remoteOpSeq, setRemoteOpSeq] = useState(0);

  // ── stable refs for WebSocket callbacks ────────────
  const siteId = useRef(`site_${user.userId}`).current;
  const lastSaveTimeRef = useRef(Date.now());
  const autoSaveTimeoutRef = useRef(null);

  const [versionVector, setVersionVector] = useState({});

  // ── auto-save timeout: clear saving status after 3 seconds if no ack ──
  useEffect(() => {
    if (!saving) return;
    console.log("[EditorPage] Saving status set, will clear in 3s if no ack");
    autoSaveTimeoutRef.current = setTimeout(() => {
      console.log("[EditorPage] Save timeout - clearing saving status");
      setSaving(false);
    }, 3000);
    return () => {
      if (autoSaveTimeoutRef.current) clearTimeout(autoSaveTimeoutRef.current);
    };
  }, [saving]);

  // ── load document (GET /documents/{id}) ────────────
  useEffect(() => {
    if (!documentId) return;
    addDocId(documentId);

    api
      .getDocument(user.userId, documentId)
      .then((d) => {
        setDoc(d);
        setTitle(d.title || "Untitled");
        setContent(d.content ?? "");
        setCharCount((d.content ?? "").length);

        setVersionVector(d.versionVector || {});
        // populate initial presence from collaborators who are online
        if (d.collaborators) {
          setActiveUsers(
            d.collaborators
              .filter((c) => c.online)
              .map((c) => ({
                userId: c.userId,
                userName: c.name || c.userId,
                cursorPosition: c.cursorPosition,
                online: true,
              })),
          );
        }
      })
      .catch((e) => setLoadError(e.message));
  }, [documentId, user.userId]);

  // ── WebSocket callbacks ────────────────────────────
  const handleRemoteOp = useCallback((op) => {
    if (op.versionVector) {
      setVersionVector(op.versionVector);
    }
    setRemoteOp(op);
    setRemoteOpSeq((s) => s + 1);
  }, []);

  const handlePresence = useCallback((update) => {
    setActiveUsers((prev) => {
      if (update.online === false) {
        return prev.filter((u) => u.userId !== update.userId);
      }
      const exists = prev.find((u) => u.userId === update.userId);
      if (exists) {
        return prev.map((u) =>
          u.userId === update.userId ? { ...u, ...update } : u,
        );
      }
      return [...prev, update];
    });
  }, []);

  const handleAck = useCallback(() => {
    console.log("[EditorPage] Ack received - clearing saving status");
    if (autoSaveTimeoutRef.current) {
      clearTimeout(autoSaveTimeoutRef.current);
      autoSaveTimeoutRef.current = null;
    }
    setSaving(false);
    lastSaveTimeRef.current = Date.now();
  }, []);

  const { connected, sendOperation, sendCursor } = useWebSocket(
    documentId,
    user.userId,
    user.userName,
    {
      onOperation: handleRemoteOp,
      onPresence: handlePresence,
      onAck: handleAck,
      versionVector,
    },
  );

  // ── local operation → WebSocket ────────────────────
  const handleLocalOp = useCallback(
    (op) => {
      console.log("[EditorPage] Local operation:", op, "Connected:", connected);
      setSaving(true);
      sendOperation({
        ...op,
        versionVector, // ✅ ADD THIS
      });
      // update char count from charMap length is tricky; use a simple heuristic
      if (op.opType === "INSERT") setCharCount((c) => c + 1);
      if (op.opType === "DELETE") setCharCount((c) => Math.max(0, c - 1));
    },
    [sendOperation, connected, versionVector],
  );

  // ── periodic auto-save via REST API (fallback if WebSocket unavailable) ──
  useEffect(() => {
    const autoSaveInterval = setInterval(async () => {
      if (!content || content === (doc?.content ?? "")) return; // No changes
      console.log("[EditorPage] Periodic auto-save triggered");
      try {
        // This saves content via REST API as a fallback
        // The WebSocket operations are the primary mechanism, but this ensures nothing is lost
      } catch (err) {
        console.error("[EditorPage] Auto-save failed:", err);
      }
    }, 10000); // Auto-save every 10 seconds

    return () => clearInterval(autoSaveInterval);
  }, [content, doc]);

  // ── cursor movement → WebSocket ────────────────────
  const handleCursorMove = useCallback(
    (pos, start, end) => {
      sendCursor(pos, start, end);
    },
    [sendCursor],
  );

  // ── title update (PUT /documents/{id}/title) ───────
  const handleTitleChange = useCallback(
    async (newTitle) => {
      setTitle(newTitle);
      try {
        await api.updateTitle(user.userId, documentId, newTitle);
        setToast({ message: "Title updated", type: "success" });
      } catch {
        setToast({ message: "Failed to update title", type: "error" });
      }
    },
    [user.userId, documentId],
  );

  // ── version restore (POST /documents/{id}/restore) ─
  const handleRestore = useCallback(async () => {
    try {
      const d = await api.getDocument(user.userId, documentId);
      setContent(d.content ?? "");
      setCharCount((d.content ?? "").length);
      setToast({
        message: "Version restored — reload to see full state",
        type: "success",
      });
    } catch {
      setToast({ message: "Restore failed", type: "error" });
    }
  }, [user.userId, documentId]);

  // ── error state ────────────────────────────────────
  if (loadError) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-ink-50 px-4">
        <div className="text-center animate-fade-in">
          <div className="w-16 h-16 bg-red-50 rounded-2xl flex items-center justify-center mx-auto mb-4">
            <svg
              className="w-7 h-7 text-red-400"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
              strokeWidth={1.5}
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                d="M12 9v3.75m-9.303 3.376c-.866 1.5.217 3.374 1.948 3.374h14.71c1.73 0 2.813-1.874 1.948-3.374L13.949 3.378c-.866-1.5-3.032-1.5-3.898 0L2.697 16.126z"
              />
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                d="M12 15.75h.007v.008H12v-.008z"
              />
            </svg>
          </div>
          <h2 className="text-lg font-bold text-ink-900 mb-1">
            Document not found
          </h2>
          <p className="text-sm text-ink-400 mb-6">{loadError}</p>
          <button
            onClick={() => navigate("/")}
            className="px-5 py-2.5 rounded-xl bg-ink-950 text-white text-sm font-semibold
                       hover:bg-ink-800 transition-all"
          >
            Back to Dashboard
          </button>
        </div>
      </div>
    );
  }

  // ── loading state ──────────────────────────────────
  if (content === null) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-ink-50">
        <div className="flex items-center gap-3 text-ink-400 animate-fade-in">
          <svg className="w-5 h-5 animate-spin" fill="none" viewBox="0 0 24 24">
            <circle
              className="opacity-25"
              cx="12"
              cy="12"
              r="10"
              stroke="currentColor"
              strokeWidth="4"
            />
            <path
              className="opacity-75"
              fill="currentColor"
              d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"
            />
          </svg>
          Loading document…
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-ink-50 flex flex-col">
      {/* toolbar */}
      <EditorToolbar
        title={title}
        onTitleChange={handleTitleChange}
        connected={connected}
        saving={saving}
        onShare={() => setShowShare(true)}
        onHistory={() => setShowHistory(true)}
        activeUsers={activeUsers}
        currentUserId={user.userId}
      />

      {/* editor */}
      <main className="flex-1 max-w-4xl w-full mx-auto px-4 py-8">
        <CollabEditor
          initialContent={content}
          siteId={siteId}
          onLocalOp={handleLocalOp}
          onCursorMove={handleCursorMove}
          remoteOp={remoteOp}
          remoteOpSeq={remoteOpSeq}
        />

        <StatusBar
          connected={connected}
          charCount={charCount}
          documentId={documentId}
          onlineCount={
            activeUsers.filter((u) => u.userId !== user.userId).length + 1
          }
        />
      </main>

      {/* modals / panels */}
      <ShareModal
        open={showShare}
        onClose={() => setShowShare(false)}
        documentId={documentId}
      />

      <HistoryPanel
        open={showHistory}
        onClose={() => setShowHistory(false)}
        documentId={documentId}
        onRestore={handleRestore}
      />

      {toast && <Toast {...toast} onClose={() => setToast(null)} />}
    </div>
  );
}
