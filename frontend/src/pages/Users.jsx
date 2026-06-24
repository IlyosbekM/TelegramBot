import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { getUsers, getShops, makeSeller, demoteUser } from '../api'

const roleClass = (r) => 'badge badge-' + String(r).toLowerCase()

export default function Users() {
  const [rows, setRows] = useState(null)
  const [err, setErr] = useState(false)
  const [q, setQ] = useState('')
  const navigate = useNavigate()

  // shops for modal select
  const [shops, setShops] = useState([])

  // modal state
  const [modal, setModal] = useState(null) // { user } or null
  const [selectedShopId, setSelectedShopId] = useState('')
  const [acting, setActing] = useState(false)

  // action feedback
  const [msg, setMsg] = useState(null) // { ok: bool, text: string }

  const exportCsv = () => { window.location.href = '/api/export/users.csv' }

  const load = () => {
    getUsers()
      .then(setRows)
      .catch((e) => (e.status === 401 ? navigate('/login') : setErr(true)))
  }

  useEffect(() => {
    load()
    getShops()
      .then(setShops)
      .catch((e) => { if (e.status === 401) navigate('/login') })
  }, [navigate]) // eslint-disable-line react-hooks/exhaustive-deps

  const filtered = useMemo(() => {
    if (!rows) return []
    const s = q.trim().toLowerCase()
    if (!s) return rows
    return rows.filter(
      (u) =>
        (u.fullName || '').toLowerCase().includes(s) ||
        (u.username || '').toLowerCase().includes(s) ||
        (u.phone || '').toLowerCase().includes(s) ||
        (u.role || '').toLowerCase().includes(s) ||
        (u.shopName || '').toLowerCase().includes(s)
    )
  }, [rows, q])

  const openModal = (user) => {
    setModal({ user })
    setSelectedShopId(shops.length > 0 ? String(shops[0].id) : '')
    setMsg(null)
  }

  const closeModal = () => {
    setModal(null)
    setSelectedShopId('')
  }

  const handleMakeSeller = async () => {
    if (!selectedShopId) return
    setActing(true)
    try {
      await makeSeller(modal.user.telegramId, selectedShopId)
      closeModal()
      setMsg({ ok: true, text: '✅ Foydalanuvchi sotuvchi qilindi' })
      load()
    } catch (e) {
      if (e.status === 401) { navigate('/login'); return }
      setMsg({ ok: false, text: e.message || 'Xatolik yuz berdi' })
      closeModal()
    } finally {
      setActing(false)
    }
  }

  const handleDemote = async (user) => {
    if (!window.confirm('Klient qilinsinmi?')) return
    setMsg(null)
    try {
      await demoteUser(user.telegramId)
      setMsg({ ok: true, text: '✅ Foydalanuvchi klient qilindi' })
      load()
    } catch (e) {
      if (e.status === 401) { navigate('/login'); return }
      setMsg({ ok: false, text: e.message || 'Xatolik yuz berdi' })
    }
  }

  if (err) return <div className="error-box">Xatolik yuz berdi</div>
  if (!rows) return <div className="loading">Yuklanmoqda…</div>

  return (
    <div>
      <div className="page-head">
        <h2 className="page-title">👥 Foydalanuvchilar</h2>
        <input className="search" placeholder="🔍 Qidirish…" value={q} onChange={(e) => setQ(e.target.value)} />
        <button className="export-btn" onClick={exportCsv}>⬇️ CSV</button>
      </div>

      {msg && (
        <div className={msg.ok ? 'msg-ok' : 'msg-err'}>{msg.text}</div>
      )}

      {filtered.length === 0 ? (
        <p className="empty">Foydalanuvchi yo'q</p>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>Ism</th>
                <th>Username</th>
                <th>Telefon</th>
                <th>Rol</th>
                <th>Do'kon</th>
                <th>Amal</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((u) => (
                <tr key={u.telegramId}>
                  <td>{u.telegramId}</td>
                  <td>{u.fullName}</td>
                  <td>{u.username}</td>
                  <td>{u.phone}</td>
                  <td><span className={roleClass(u.role)}>{u.role}</span></td>
                  <td>{u.shopName}</td>
                  <td>
                    <div className="actions">
                      {u.role === 'CLIENT' && (
                        <button className="btn btn-sm btn-primary" onClick={() => openModal(u)}>
                          ➡️ Sotuvchi
                        </button>
                      )}
                      {u.role === 'SELLER' && (
                        <button className="btn btn-sm btn-danger" onClick={() => handleDemote(u)}>
                          ⬇️ Klient
                        </button>
                      )}
                      {u.role === 'ADMIN' && '—'}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {modal && (
        <div className="modal-overlay" onClick={closeModal}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <h3>Sotuvchi qilish</h3>
            <p><strong>{modal.user.fullName}</strong> uchun do'kon tanlang:</p>
            <select
              value={selectedShopId}
              onChange={(e) => setSelectedShopId(e.target.value)}
              disabled={acting}
            >
              {shops.length === 0 && <option value="">Do'kon yo'q</option>}
              {shops.map((s) => (
                <option key={s.id} value={String(s.id)}>{s.name}</option>
              ))}
            </select>
            <div className="modal-actions">
              <button
                className="btn btn-green"
                onClick={handleMakeSeller}
                disabled={acting || !selectedShopId}
              >
                {acting ? 'Tayinlanmoqda…' : 'Tayinlash'}
              </button>
              <button className="btn btn-gray" onClick={closeModal} disabled={acting}>
                Bekor
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
