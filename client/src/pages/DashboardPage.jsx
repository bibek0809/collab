import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useUser } from '../context/UserContext';
import * as api from '../api/documentApi';
import { getSavedDocIds, addDocId, removeDocId, truncate, timeAgo } from '../utils/helpers';
import Modal from '../components/ui/Modal';
import Toast from '../components/ui/Toast';
import Avatar from '../components/ui/Avatar';

export default function DashboardPage() {
  const { user, logout } = useUser();
  const navigate = useNavigate();
  const [docs, setDocs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showCreate, setShowCreate] = useState(false);
  const [newTitle, setNewTitle] = useState('');
  const [toast, setToast] = useState(null);

  // ── load all saved docs ───────────────────────────
  const loadDocs = useCallback(async () => {
    setLoading(true);
    try {
      const ids = getSavedDocIds();
      if (!ids || ids.length === 0) {
        setDocs([]);
        setLoading(false);
        return;
      }
      
      const results = [];
      const timeoutPromise = new Promise((_, reject) =>
        setTimeout(() => reject(new Error('Load timeout')), 5000)
      );
      
      for (const id of ids) {
        try {
          const docPromise = Promise.race([
            api.getDocument(user.userId, id),
            timeoutPromise,
          ]);
          const doc = await docPromise;
          results.push(doc);
        } catch {
          /* document deleted, inaccessible, or timed out — skip */
        }
      }
      setDocs(results);
    } catch (err) {
      console.error('Failed to load docs:', err);
      setDocs([]);
    } finally {
      setLoading(false);
    }
  }, [user.userId]);

  useEffect(() => {
    loadDocs();
  }, [loadDocs]);

  // ── create document ───────────────────────────────
  const handleCreate = async () => {
    if (!newTitle.trim()) return;
    try {
      const doc = await api.createDocument(user.userId, newTitle.trim());
      addDocId(doc.documentId);
      setNewTitle('');
      setShowCreate(false);
      setToast({ message: 'Document created', type: 'success' });
      navigate(`/doc/${doc.documentId}`);
    } catch (e) {
      setToast({ message: e.message, type: 'error' });
    }
  };

  // ── delete document ───────────────────────────────
  const handleDelete = async (e, docId) => {
    e.stopPropagation();
    if (!window.confirm('Delete this document permanently?')) return;
    try {
      await api.deleteDocument(user.userId, docId);
      removeDocId(docId);
      setToast({ message: 'Document deleted', type: 'success' });
      loadDocs();
    } catch (e) {
      setToast({ message: e.message, type: 'error' });
    }
  };

  // ── open by ID ────────────────────────────────────
  const handleOpenById = () => {
    const id = window.prompt('Enter a document ID to open:');
    if (id?.trim()) {
      addDocId(id.trim());
      navigate(`/doc/${id.trim()}`);
    }
  };

  return (
    <div className="min-h-screen bg-ink-50">
      {/* ── header ─────────────────────────────────── */}
      <header className="bg-white border-b border-ink-100">
        <div className="max-w-5xl mx-auto px-6 py-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 bg-ink-950 rounded-xl flex items-center justify-center">
              <span className="text-sm font-mono font-bold text-white tracking-tight">CE</span>
            </div>
            <h1 className="text-lg font-bold text-ink-900 hidden sm:block">CollabEdit</h1>
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={handleOpenById}
              className="px-3 py-2 text-sm font-medium text-ink-500
                         hover:text-ink-800 hover:bg-ink-100 rounded-lg transition-colors"
            >
              Open by ID
            </button>
            <div className="flex items-center gap-2 bg-ink-50 pl-1 pr-3 py-1 rounded-full">
              <Avatar userId={user.userId} name={user.userName} size="sm" />
              <span className="text-sm font-medium text-ink-600">{user.userName}</span>
            </div>
            <button
              onClick={logout}
              className="text-xs text-ink-400 hover:text-red-500 px-2 py-1 rounded transition-colors"
              title="Log out"
            >
              Sign out
            </button>
          </div>
        </div>
      </header>

      {/* ── content ────────────────────────────────── */}
      <main className="max-w-5xl mx-auto px-6 py-10">
        <div className="flex items-end justify-between mb-8">
          <div>
            <h2 className="text-2xl font-bold text-ink-900">Your Documents</h2>
            <p className="text-ink-400 text-sm mt-1">
              {docs.length} document{docs.length !== 1 ? 's' : ''}
            </p>
          </div>
          <button
            onClick={() => setShowCreate(true)}
            className="flex items-center gap-2 bg-ink-950 text-white px-5 py-2.5
                       rounded-xl font-semibold text-sm hover:bg-ink-800
                       transition-all shadow-sm active:scale-[0.97]"
          >
            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 5v14m-7-7h14" />
            </svg>
            New Document
          </button>
        </div>

        {/* loading skeleton */}
        {loading && (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {[0, 1, 2].map((i) => (
              <div key={i} className="bg-white rounded-2xl border border-ink-100 p-5 animate-pulse">
                <div className="w-10 h-10 bg-ink-100 rounded-xl mb-4" />
                <div className="h-4 bg-ink-100 rounded w-2/3 mb-2" />
                <div className="h-3 bg-ink-50 rounded w-1/2" />
              </div>
            ))}
          </div>
        )}

        {/* empty state */}
        {!loading && docs.length === 0 && (
          <div className="text-center py-20 animate-fade-in">
            <div className="w-20 h-20 bg-ink-100 rounded-3xl flex items-center justify-center mx-auto mb-5">
              <svg className="w-8 h-8 text-ink-300" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                <path strokeLinecap="round" strokeLinejoin="round"
                      d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z" />
                <path strokeLinecap="round" strokeLinejoin="round" d="M14 2v6h6M16 13H8m8 4H8" />
              </svg>
            </div>
            <p className="text-ink-500 text-lg font-semibold">No documents yet</p>
            <p className="text-ink-300 text-sm mt-1">
              Create your first document or open one by ID.
            </p>
          </div>
        )}

        {/* document grid */}
        {!loading && docs.length > 0 && (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {docs.map((doc, i) => (
              <div
                key={doc.documentId}
                className="doc-card bg-white rounded-2xl border border-ink-100 p-5
                           cursor-pointer group animate-fade-in"
                style={{ animationDelay: `${i * 50}ms` }}
                onClick={() => navigate(`/doc/${doc.documentId}`)}
              >
                <div className="flex items-start justify-between mb-3">
                  <div className="w-10 h-10 bg-accent/[0.08] rounded-xl flex items-center justify-center">
                    <svg className="w-5 h-5 text-accent" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                      <path strokeLinecap="round" strokeLinejoin="round"
                            d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z" />
                      <path strokeLinecap="round" strokeLinejoin="round" d="M14 2v6h6M16 13H8m8 4H8" />
                    </svg>
                  </div>
                  <button
                    onClick={(e) => handleDelete(e, doc.documentId)}
                    className="p-1.5 rounded-lg text-ink-300 hover:text-red-500
                               hover:bg-red-50 opacity-0 group-hover:opacity-100
                               transition-all"
                    title="Delete"
                  >
                    <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                      <path strokeLinecap="round" strokeLinejoin="round"
                            d="M3 6h18M19 6v14a2 2 0 01-2 2H7a2 2 0 01-2-2V6m3 0V4a2 2 0 012-2h4a2 2 0 012 2v2" />
                    </svg>
                  </button>
                </div>

                <h3 className="font-semibold text-ink-900 truncate">{doc.title}</h3>
                <p className="text-[11px] text-ink-400 mt-0.5 font-mono">{doc.documentId}</p>
                <p className="text-xs text-ink-300 mt-2 line-clamp-2">
                  {doc.content ? truncate(doc.content, 90) : 'Empty document'}
                </p>

                {doc.updatedAt && (
                  <p className="text-[10px] text-ink-300 mt-3">{timeAgo(doc.updatedAt)}</p>
                )}
              </div>
            ))}
          </div>
        )}
      </main>

      {/* ── create modal ───────────────────────────── */}
      <Modal open={showCreate} onClose={() => { setShowCreate(false); setNewTitle(''); }} title="New Document">
        <form onSubmit={(e) => { e.preventDefault(); handleCreate(); }}>
          <input
            type="text"
            value={newTitle}
            onChange={(e) => setNewTitle(e.target.value)}
            placeholder="Document title"
            autoFocus
            className="w-full px-4 py-3 rounded-xl border-2 border-ink-200
                       focus:border-accent focus:outline-none font-medium
                       text-ink-800 placeholder:text-ink-300 transition-colors"
          />
          <button
            type="submit"
            disabled={!newTitle.trim()}
            className="mt-4 w-full py-3 rounded-xl bg-ink-950 text-white font-semibold
                       hover:bg-ink-800 disabled:opacity-25 disabled:cursor-not-allowed
                       transition-all active:scale-[0.98]"
          >
            Create
          </button>
        </form>
      </Modal>

      {toast && <Toast {...toast} onClose={() => setToast(null)} />}
    </div>
  );
}
