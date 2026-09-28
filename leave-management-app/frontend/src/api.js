/* Thin API client + formatting helpers shared by every component.
 * Same contract as the backend's REST API; errors always surface the
 * backend's {"status", "message"} shape. */
export async function api(method, path, body) {
  const res = await fetch(path, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  let data = null
  try { data = await res.json() } catch (_) { /* empty body */ }
  if (!res.ok) {
    throw new Error((data && data.message) || `HTTP ${res.status}`)
  }
  return data
}

export function fmtDate(iso) {
  if (!iso) return '–'
  const [y, m, d] = iso.split('-')
  return `${d}/${m}/${y}`
}

export function fmtWhen(iso) {
  if (!iso) return ''
  return new Date(iso).toLocaleString()
}

export const YEAR = new Date().getFullYear()
