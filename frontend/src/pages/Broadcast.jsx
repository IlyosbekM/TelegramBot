import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { getShops, getUsers, sendBroadcast } from '../api'

export default function Broadcast() {
  const navigate = useNavigate()

  const [target, setTarget] = useState('all')
  const [shops, setShops] = useState([])
  const [users, setUsers] = useState([])
  const [shopId, setShopId] = useState('')
  const [selectedIds, setSelectedIds] = useState([])
  const [text, setText] = useState('')
  const [sending, setSending] = useState(false)
  const [msg, setMsg] = useState(null) // { type: 'ok'|'err', text: string }

  useEffect(() => {
    async function loadData() {
      try {
        const [shopList, userList] = await Promise.all([getShops(), getUsers()])
        setShops(shopList)
        if (shopList.length > 0) setShopId(String(shopList[0].id))
        setUsers(userList)
      } catch (e) {
        if (e.status === 401) return navigate('/login')
        setMsg({ type: 'err', text: e.message || 'Ma\'lumotlarni yuklab bo\'lmadi' })
      }
    }
    loadData()
  }, [])

  function toggleId(telegramId) {
    setSelectedIds((prev) =>
      prev.includes(telegramId)
        ? prev.filter((id) => id !== telegramId)
        : [...prev, telegramId]
    )
  }

  async function handleSubmit(e) {
    e.preventDefault()
    if (!text.trim()) return
    setMsg(null)
    setSending(true)
    try {
      const result = await sendBroadcast({
        target,
        shopId: target === 'shop' ? Number(shopId) : null,
        ids: target === 'ids' ? selectedIds : null,
        text: text.trim(),
      })
      setMsg({ type: 'ok', text: `✅ Xabar ${result.sent} ta foydalanuvchiga yuborildi` })
      setText('')
      setSelectedIds([])
    } catch (e) {
      if (e.status === 401) return navigate('/login')
      setMsg({ type: 'err', text: e.message || 'Xabar yuborilmadi' })
    } finally {
      setSending(false)
    }
  }

  return (
    <div>
      <h2 className="page-title">📢 Ommaviy xabar</h2>

      {msg && (
        <div className={msg.type === 'ok' ? 'msg-ok' : 'msg-err'}>
          {msg.text}
        </div>
      )}

      <div className="panel">
        <form onSubmit={handleSubmit} className="form-grid">
          {/* Target selector */}
          <div className="field">
            <label htmlFor="target">Kimga</label>
            <select
              id="target"
              value={target}
              onChange={(e) => {
                setTarget(e.target.value)
                setSelectedIds([])
                setMsg(null)
              }}
            >
              <option value="all">👥 Hammaga</option>
              <option value="shop">🏪 Do'kon bo'yicha</option>
              <option value="ids">✅ Tanlab</option>
            </select>
          </div>

          {/* Shop selector (only when target === 'shop') */}
          {target === 'shop' && (
            <div className="field">
              <label htmlFor="shopId">Do'kon</label>
              <select
                id="shopId"
                value={shopId}
                onChange={(e) => setShopId(e.target.value)}
              >
                {shops.length === 0 && (
                  <option value="">Do'konlar yo'q</option>
                )}
                {shops.map((s) => (
                  <option key={s.id} value={String(s.id)}>
                    {s.name}
                  </option>
                ))}
              </select>
            </div>
          )}

          {/* User checkbox list (only when target === 'ids') */}
          {target === 'ids' && (
            <div className="field">
              <label>Foydalanuvchilar</label>
              <div
                style={{
                  maxHeight: '220px',
                  overflowY: 'auto',
                  border: '1px solid var(--border, #ddd)',
                  borderRadius: '6px',
                  padding: '8px',
                  display: 'flex',
                  flexDirection: 'column',
                  gap: '6px',
                }}
              >
                {users.length === 0 && (
                  <span className="empty">Foydalanuvchilar yo'q</span>
                )}
                {users.map((u) => (
                  <label
                    key={u.telegramId}
                    style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer' }}
                  >
                    <input
                      type="checkbox"
                      checked={selectedIds.includes(u.telegramId)}
                      onChange={() => toggleId(u.telegramId)}
                    />
                    {u.fullName} ({u.role})
                    {u.username ? ` @${u.username}` : ''}
                  </label>
                ))}
              </div>
              {target === 'ids' && (
                <span style={{ fontSize: '0.82rem', opacity: 0.65, marginTop: '4px' }}>
                  Tanlangan: {selectedIds.length} ta
                </span>
              )}
            </div>
          )}

          {/* Message textarea */}
          <div className="field">
            <label htmlFor="broadcastText">Matn</label>
            <textarea
              id="broadcastText"
              value={text}
              onChange={(e) => setText(e.target.value)}
              rows={5}
              required
              placeholder="Xabar matnini kiriting..."
            />
          </div>

          <div className="actions">
            <button
              type="submit"
              className="btn btn-green"
              disabled={sending || !text.trim() || (target === 'ids' && selectedIds.length === 0)}
            >
              {sending ? 'Yuborilmoqda...' : '📤 Yuborish'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
