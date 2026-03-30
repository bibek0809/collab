const PALETTE = [
  '#e85d26', '#3b82f6', '#10b981', '#8b5cf6',
  '#ec4899', '#f59e0b', '#06b6d4', '#ef4444',
  '#6366f1', '#14b8a6', '#f97316', '#a855f7',
];

export function userColor(id = '') {
  const hash = [...id].reduce((a, c) => a + c.charCodeAt(0), 0);
  return PALETTE[Math.abs(hash) % PALETTE.length];
}

export function initials(name = '') {
  return name
    .split(/\s+/)
    .slice(0, 2)
    .map((w) => w[0]?.toUpperCase() ?? '')
    .join('');
}

export function timeAgo(iso) {
  if (!iso) return '';
  const s = Math.floor((Date.now() - new Date(iso).getTime()) / 1000);
  if (s < 60) return 'just now';
  if (s < 3600) return `${Math.floor(s / 60)}m ago`;
  if (s < 86400) return `${Math.floor(s / 3600)}h ago`;
  return `${Math.floor(s / 86400)}d ago`;
}

export function truncate(str = '', len = 100) {
  return str.length > len ? str.substring(0, len) + '…' : str;
}

// ── Local doc‑id storage (no list endpoint on backend) ─

const STORAGE_KEY = 'collab_doc_ids';

export function getSavedDocIds() {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]');
  } catch {
    return [];
  }
}

export function addDocId(id) {
  const ids = getSavedDocIds().filter((i) => i !== id);
  ids.unshift(id);
  localStorage.setItem(STORAGE_KEY, JSON.stringify(ids));
}

export function removeDocId(id) {
  const ids = getSavedDocIds().filter((i) => i !== id);
  localStorage.setItem(STORAGE_KEY, JSON.stringify(ids));
}
