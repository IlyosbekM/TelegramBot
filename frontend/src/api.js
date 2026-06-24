// Barcha so'rovlar session cookie bilan (credentials:'include'). Dev'da /api Vite proxy orqali 8080 ga.
async function request(path, options = {}) {
  const res = await fetch(path, {
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    ...options,
  })
  if (!res.ok) {
    let msg = 'Xatolik: ' + res.status
    try {
      const j = await res.json()
      if (j && j.error) msg = j.error
    } catch (e) {
      /* ignore */
    }
    const err = new Error(msg)
    err.status = res.status
    throw err
  }
  const text = await res.text()
  return text ? JSON.parse(text) : null
}

export const apiGet = (path) => request(path, { method: 'GET' })
export const apiPost = (path, body) =>
  request(path, { method: 'POST', body: body ? JSON.stringify(body) : undefined })
export const apiPut = (path, body) =>
  request(path, { method: 'PUT', body: body ? JSON.stringify(body) : undefined })
export const apiDelete = (path) => request(path, { method: 'DELETE' })

// ── Auth ──────────────────────────────────────────────
export const login = (password) => apiPost('/api/login', { password })
export const logout = () => apiPost('/api/logout')
export const getMe = () => apiGet('/api/me')

// ── Read (ko'rish) ────────────────────────────────────
export const getDashboard = () => apiGet('/api/dashboard')
export const getShops = () => apiGet('/api/shops')
export const getUsers = () => apiGet('/api/users')
export const getDebts = () => apiGet('/api/debts')
export const getRequests = () => apiGet('/api/admin/requests')
export const getShopClients = (shopId) => apiGet(`/api/admin/shops/${shopId}/clients`)

// ── Shops ─────────────────────────────────────────────
export const createShop = (name, address) => apiPost('/api/admin/shops', { name, address })

// ── Users / rollar ────────────────────────────────────
export const makeSeller = (userId, shopId) => apiPost(`/api/admin/users/${userId}/make-seller`, { shopId })
export const demoteUser = (userId) => apiPost(`/api/admin/users/${userId}/demote`)

// ── Debts (qarzlar) ───────────────────────────────────
export const createDebt = (payload) => apiPost('/api/admin/debts', payload)
export const payDebt = (id, amount, note) => apiPost(`/api/admin/debts/${id}/pay`, { amount, note })
export const increaseDebt = (id, amount, note) => apiPost(`/api/admin/debts/${id}/increase`, { amount, note })
export const editDebt = (id, payload) => apiPut(`/api/admin/debts/${id}`, payload)
export const deleteDebt = (id) => apiDelete(`/api/admin/debts/${id}`)
export const remindDebt = (id) => apiPost(`/api/admin/debts/${id}/remind`)

// ── Broadcast ─────────────────────────────────────────
export const sendBroadcast = (payload) => apiPost('/api/admin/broadcast', payload)

// ── Requests (so'rovlar) ──────────────────────────────
export const approveShopReq = (id) => apiPost(`/api/admin/requests/shop/${id}/approve`)
export const rejectShopReq = (id, reason) => apiPost(`/api/admin/requests/shop/${id}/reject`, { reason })
export const acceptMembership = (id) => apiPost(`/api/admin/requests/membership/${id}/accept`)
export const rejectMembership = (id) => apiPost(`/api/admin/requests/membership/${id}/reject`)
export const confirmPayment = (id) => apiPost(`/api/admin/requests/payment/${id}/confirm`)
export const rejectPayment = (id) => apiPost(`/api/admin/requests/payment/${id}/reject`)
