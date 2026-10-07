const P = window.location.pathname;
export const BASE = (P === '/auth' || P.startsWith('/auth/')) ? '/auth' : '';

let basic = null;
let domain = null;

export function setAuth(b, d) { basic = b; domain = d || null; }
export function hasAuth() { return !!basic; }

function headers(json) {
  const h = {};
  if (json) h['Content-Type'] = 'application/json';
  if (basic) h['Authorization'] = basic;
  if (domain) h['X-Domain'] = domain;
  return h;
}

export async function api(path, { method = 'GET', body = null } = {}) {
  const r = await fetch(BASE + path, {
    method,
    headers: headers(body !== null),
    body: body !== null ? JSON.stringify(body) : undefined
  });
  let data = null;
  try { data = await r.json(); } catch (e) { /* sem corpo */ }
  return { ok: r.ok, status: r.status, data };
}

export function basicOf(u, p) { return 'Basic ' + btoa(u + ':' + p); }
export function qrUrl(id) { return BASE + '/api/v1/mfa/qr/' + id + '.png'; }
export function downloadUrl(token) { return BASE + '/api/v1/certificates/download?token=' + encodeURIComponent(token); }
