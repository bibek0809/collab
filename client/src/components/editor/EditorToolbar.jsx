import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Avatar from '../ui/Avatar';

export default function EditorToolbar({
  title,
  onTitleChange,
  connected,
  saving,
  onShare,
  onHistory,
  activeUsers,
  currentUserId,
}) {
  const navigate = useNavigate();
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(title);

  const others = activeUsers.filter((u) => u.userId !== currentUserId);

  const commitTitle = () => {
    setEditing(false);
    if (draft.trim() && draft !== title) onTitleChange(draft.trim());
  };

  return (
    <header className="bg-white border-b border-ink-100 sticky top-0 z-20">
      <div className="max-w-4xl mx-auto px-4 py-3 flex items-center justify-between gap-3">
        {/* left: back + title */}
        <div className="flex items-center gap-2 min-w-0">
          <button
            onClick={() => navigate('/')}
            className="p-2 rounded-lg hover:bg-ink-100 text-ink-400
                       hover:text-ink-700 transition-colors shrink-0"
            title="Back to dashboard"
          >
            <svg className="w-[18px] h-[18px]" fill="none" viewBox="0 0 24 24"
                 stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M19 12H5m7 7l-7-7 7-7" />
            </svg>
          </button>

          {editing ? (
            <input
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              onBlur={commitTitle}
              onKeyDown={(e) => e.key === 'Enter' && commitTitle()}
              className="text-lg font-bold text-ink-900 bg-transparent border-b-2
                         border-accent outline-none py-0.5 px-1 min-w-0"
              autoFocus
            />
          ) : (
            <h1
              className="text-lg font-bold text-ink-900 truncate cursor-pointer
                         hover:text-accent transition-colors"
              onClick={() => { setDraft(title); setEditing(true); }}
            >
              {title}
            </h1>
          )}

          {saving && (
            <span className="text-[11px] text-ink-300 font-medium shrink-0 ml-1">
              Saving…
            </span>
          )}
        </div>

        {/* right: avatars + actions */}
        <div className="flex items-center gap-2 shrink-0">
          {/* avatar stack */}
          <div className="flex items-center -space-x-2 mr-1">
            <Avatar userId={currentUserId} name="You" size="sm" ring className="z-10" />
            {others.slice(0, 5).map((u, i) => (
              <Avatar
                key={u.userId}
                userId={u.userId}
                name={u.userName || u.userId}
                size="sm"
                ring
                className={`z-[${9 - i}]`}
              />
            ))}
            {others.length > 5 && (
              <div className="w-6 h-6 rounded-full ring-2 ring-white bg-ink-200
                              flex items-center justify-center text-[9px] font-bold text-ink-500">
                +{others.length - 5}
              </div>
            )}
          </div>

          {/* connection dot */}
          <span
            className={`w-2 h-2 rounded-full shrink-0 ${connected ? 'bg-emerald-500' : 'bg-red-400'}`}
            title={connected ? 'Connected' : 'Disconnected'}
          />

          <button onClick={onShare} className="toolbar-btn">
            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round"
                    d="M4 12v8a2 2 0 002 2h12a2 2 0 002-2v-8M16 6l-4-4-4 4M12 2v13" />
            </svg>
            <span className="hidden sm:inline">Share</span>
          </button>

          <button onClick={onHistory} className="toolbar-btn">
            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <circle cx="12" cy="12" r="10" />
              <polyline points="12 6 12 12 16 14" />
            </svg>
            <span className="hidden sm:inline">History</span>
          </button>
        </div>
      </div>

      {/* presence tags */}
      {others.length > 0 && (
        <div className="max-w-4xl mx-auto px-4 pb-2.5 flex items-center gap-1.5 overflow-x-auto">
          {others.map((u) => (
            <PresenceTag key={u.userId} user={u} />
          ))}
        </div>
      )}

      <style>{`
        .toolbar-btn {
          display: flex; align-items: center; gap: 5px;
          padding: 7px 12px; border-radius: 10px;
          font-size: 13px; font-weight: 500;
          color: #918779; transition: all 0.15s;
        }
        .toolbar-btn:hover { color: #292623; background: #edecea; }
      `}</style>
    </header>
  );
}

function PresenceTag({ user }) {
  const bg = `${
    ['#e85d26','#3b82f6','#10b981','#8b5cf6','#ec4899','#f59e0b','#06b6d4','#ef4444'][
      Math.abs([...(user.userId || '')].reduce((a,c) => a + c.charCodeAt(0), 0)) % 8
    ]
  }20`;
  const fg =
    ['#e85d26','#3b82f6','#10b981','#8b5cf6','#ec4899','#f59e0b','#06b6d4','#ef4444'][
      Math.abs([...(user.userId || '')].reduce((a,c) => a + c.charCodeAt(0), 0)) % 8
    ];

  return (
    <span
      className="inline-flex items-center gap-1 text-[11px] font-semibold
                 px-2.5 py-1 rounded-full whitespace-nowrap"
      style={{ background: bg, color: fg }}
    >
      <span className="w-1.5 h-1.5 rounded-full" style={{ background: fg }} />
      {user.userName || user.userId}
    </span>
  );
}
