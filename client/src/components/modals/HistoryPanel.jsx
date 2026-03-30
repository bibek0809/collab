import { useState, useEffect } from 'react';
import * as api from '../../api/documentApi';
import { truncate } from '../../utils/helpers';

export default function HistoryPanel({ open, onClose, documentId, onRestore }) {
  const [versions, setVersions] = useState([]);
  const [loading, setLoading] = useState(false);
  const [restoring, setRestoring] = useState(null);

  useEffect(() => {
    if (open && documentId) {
      setLoading(true);
      api
        .getHistory(documentId)
        .then((d) => setVersions(d.versions || []))
        .catch(() => {})
        .finally(() => setLoading(false));
    }
  }, [open, documentId]);

  const handleRestore = async (versionId) => {
    setRestoring(versionId);
    try {
      await api.restoreVersion(documentId, versionId);
      onRestore?.();
      onClose();
    } catch {
      /* handled upstream */
    } finally {
      setRestoring(null);
    }
  };

  if (!open) return null;

  return (
    <>
      {/* overlay */}
      <div className="fixed inset-0 z-30 bg-black/10" onClick={onClose} />

      {/* panel */}
      <aside className="fixed right-0 top-0 h-full w-80 bg-white border-l border-ink-100
                        shadow-2xl z-40 flex flex-col animate-slide-in-right">
        {/* header */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-ink-100">
          <h3 className="font-bold text-ink-900">Version History</h3>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg hover:bg-ink-100 text-ink-400 hover:text-ink-700 transition-colors"
          >
            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* list */}
        <div className="flex-1 overflow-y-auto p-4 space-y-3">
          {loading && (
            <div className="flex items-center justify-center py-16 text-ink-300">
              <svg className="w-5 h-5 animate-spin mr-2" fill="none" viewBox="0 0 24 24">
                <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
              </svg>
              Loading…
            </div>
          )}

          {!loading && versions.length === 0 && (
            <div className="text-center py-16">
              <p className="text-sm text-ink-400 font-medium">No snapshots yet</p>
              <p className="text-xs text-ink-300 mt-1">
                Snapshots are created automatically every 1 000 operations.
              </p>
            </div>
          )}

          {versions.map((v, i) => (
            <div
              key={v.versionId}
              className="bg-ink-50 rounded-xl p-4 animate-fade-in"
              style={{ animationDelay: `${i * 40}ms` }}
            >
              <code className="text-[11px] font-mono text-ink-400 block mb-1 truncate">
                {v.versionId}
              </code>
              <p className="text-xs text-ink-500">
                {v.createdAt ? new Date(v.createdAt).toLocaleString() : '—'}
              </p>
              <p className="text-xs text-ink-400 mt-1.5 line-clamp-2">
                {truncate(v.content, 120) || 'Empty'}
              </p>
              <button
                onClick={() => handleRestore(v.versionId)}
                disabled={restoring === v.versionId}
                className="mt-3 text-xs font-bold text-accent hover:text-accent-dark
                           disabled:opacity-40 transition-colors"
              >
                {restoring === v.versionId ? 'Restoring…' : 'Restore this version'}
              </button>
            </div>
          ))}
        </div>
      </aside>
    </>
  );
}
