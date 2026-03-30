import { useState, useEffect } from 'react';
import { useUser } from '../../context/UserContext';
import * as api from '../../api/documentApi';
import Modal from '../ui/Modal';
import Avatar from '../ui/Avatar';
import Toast from '../ui/Toast';

export default function ShareModal({ open, onClose, documentId }) {
  const { user } = useUser();
  const [targetUser, setTargetUser] = useState('');
  const [permission, setPermission] = useState('EDITOR');
  const [collaborators, setCollaborators] = useState([]);
  const [toast, setToast] = useState(null);

  useEffect(() => {
    if (open && documentId) {
      api.getCollaborators(documentId).then(setCollaborators).catch(() => {});
    }
  }, [open, documentId]);

  const handleShare = async () => {
    if (!targetUser.trim()) return;
    try {
      await api.shareDocument(user.userId, documentId, targetUser.trim(), permission);
      setToast({ message: `Shared with ${targetUser.trim()}`, type: 'success' });
      setTargetUser('');
      const fresh = await api.getCollaborators(documentId);
      setCollaborators(fresh);
    } catch (e) {
      setToast({ message: e.message, type: 'error' });
    }
  };

  const copyId = () => {
    navigator.clipboard.writeText(documentId);
    setToast({ message: 'Document ID copied', type: 'success' });
  };

  return (
    <>
      <Modal open={open} onClose={onClose} title="Share Document" wide>
        <div className="space-y-5">
          {/* invite form */}
          <div>
            <label className="block text-xs font-semibold text-ink-500 uppercase tracking-wider mb-2">
              Invite by User ID
            </label>
            <div className="flex gap-2">
              <input
                type="text"
                value={targetUser}
                onChange={(e) => setTargetUser(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && handleShare()}
                placeholder="e.g. user_john_a1b2c"
                className="flex-1 px-3 py-2.5 rounded-xl border border-ink-200 text-sm
                           font-mono focus:border-accent focus:outline-none
                           placeholder:text-ink-300"
              />
              <select
                value={permission}
                onChange={(e) => setPermission(e.target.value)}
                className="px-3 py-2.5 rounded-xl border border-ink-200 text-sm font-medium
                           focus:border-accent focus:outline-none bg-white cursor-pointer"
              >
                <option value="EDITOR">Editor</option>
                <option value="VIEWER">Viewer</option>
              </select>
            </div>
            <button
              onClick={handleShare}
              disabled={!targetUser.trim()}
              className="mt-3 w-full py-2.5 rounded-xl bg-accent text-white text-sm
                         font-semibold hover:bg-accent-dark disabled:opacity-30
                         disabled:cursor-not-allowed transition-colors"
            >
              Send Invite
            </button>
          </div>

          {/* collaborators list */}
          <div className="border-t border-ink-100 pt-4">
            <label className="block text-xs font-semibold text-ink-500 uppercase tracking-wider mb-2">
              Collaborators ({collaborators.length})
            </label>
            <div className="space-y-2 max-h-52 overflow-y-auto pr-1">
              {collaborators.map((c) => (
                <div
                  key={c.userId}
                  className="flex items-center justify-between py-2.5 px-3 rounded-xl bg-ink-50"
                >
                  <div className="flex items-center gap-2.5">
                    <Avatar userId={c.userId} name={c.name || c.userId} size="sm" />
                    <span className="text-sm font-medium text-ink-700 truncate max-w-[160px]">
                      {c.name || c.userId}
                    </span>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="text-[11px] font-mono text-ink-400 bg-ink-100 px-2 py-0.5 rounded-full">
                      {c.permission}
                    </span>
                    {c.online && (
                      <span className="w-2 h-2 rounded-full bg-emerald-500" />
                    )}
                  </div>
                </div>
              ))}
              {collaborators.length === 0 && (
                <p className="text-sm text-ink-300 text-center py-4">No collaborators yet</p>
              )}
            </div>
          </div>

          {/* document ID */}
          <div className="border-t border-ink-100 pt-4">
            <label className="block text-xs font-semibold text-ink-500 uppercase tracking-wider mb-2">
              Document ID
            </label>
            <div className="flex items-center gap-2 bg-ink-50 px-3 py-2.5 rounded-xl">
              <code className="text-xs font-mono text-ink-600 flex-1 truncate">
                {documentId}
              </code>
              <button
                onClick={copyId}
                className="text-xs font-bold text-accent hover:text-accent-dark shrink-0"
              >
                Copy
              </button>
            </div>
          </div>
        </div>
      </Modal>
      {toast && <Toast {...toast} onClose={() => setToast(null)} />}
    </>
  );
}
