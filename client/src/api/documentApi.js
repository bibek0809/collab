const BASE = '/api/v1/documents';

function headers(userId) {
  return { 'Content-Type': 'application/json', 'User-Id': userId };
}

async function request(url, opts = {}) {
  const res = await fetch(url, opts);
  if (res.status === 204) return null;
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(body.message || `Request failed: ${res.status}`);
  }
  return res.json();
}

// ── Document CRUD ────────────────────────────────────

export async function createDocument(userId, title, content = '') {
  return request(BASE, {
    method: 'POST',
    headers: headers(userId),
    body: JSON.stringify({ title, content }),
  });
}

export async function getDocument(userId, documentId) {
  return request(`${BASE}/${documentId}`, {
    headers: headers(userId),
  });
}

export async function updateTitle(userId, documentId, title) {
  return request(`${BASE}/${documentId}/title`, {
    method: 'PUT',
    headers: headers(userId),
    body: JSON.stringify({ title }),
  });
}

export async function deleteDocument(userId, documentId) {
  return request(`${BASE}/${documentId}`, {
    method: 'DELETE',
    headers: headers(userId),
  });
}

// ── Sharing ──────────────────────────────────────────

export async function shareDocument(userId, documentId, targetUserId, permission) {
  return request(`${BASE}/${documentId}/share`, {
    method: 'POST',
    headers: headers(userId),
    body: JSON.stringify({ userId: targetUserId, permission }),
  });
}

export async function getCollaborators(documentId) {
  return request(`${BASE}/${documentId}/collaborators`);
}

// ── Version History ──────────────────────────────────

export async function getHistory(documentId, limit = 50) {
  return request(`${BASE}/${documentId}/history?limit=${limit}`);
}

export async function restoreVersion(documentId, versionId) {
  return request(`${BASE}/${documentId}/restore`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ versionId }),
  });
}

// ── Health ───────────────────────────────────────────

export async function healthCheck() {
  return request('/health');
}
