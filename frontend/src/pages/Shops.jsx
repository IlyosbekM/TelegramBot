import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { getShops, createShop } from '../api'

export default function Shops() {
  const [rows, setRows] = useState(null)
  const [err, setErr] = useState(false)
  const [q, setQ] = useState('')
  const navigate = useNavigate()

  // form state
  const [showForm, setShowForm] = useState(false)
  const [name, setName] = useState('')
  const [address, setAddress] = useState('')
  const [saving, setSaving] = useState(false)
  const [msg, setMsg] = useState(null) // { ok: bool, text: string }

  const exportCsv = () => { window.location.href = '/api/export/shops.csv' }

  const load = () => {
    getShops()
      .then(setRows)
      .catch((e) => (e.status === 401 ? navigate('/login') : setErr(true)))
  }

  useEffect(() => {
    load()
  }, [navigate]) // eslint-disable-line react-hooks/exhaustive-deps

  const filtered = useMemo(() => {
    if (!rows) return []
    const s = q.trim().toLowerCase()
    if (!s) return rows
    return rows.filter(
      (r) =>
        String(r.id).includes(s) ||
        (r.name || '').toLowerCase().includes(s) ||
        (r.address || '').toLowerCase().includes(s)
    )
  }, [rows, q])

  const handleSave = async () => {
    if (!name.trim()) return
    setSaving(true)
    setMsg(null)
    try {
      await createShop(name.trim(), address.trim())
      setShowForm(false)
      setName('')
      setAddress('')
      setMsg({ ok: true, text: "✅ Do'kon qo'shildi" })
      load()
    } catch (e) {
      if (e.status === 401) { navigate('/login'); return }
      setMsg({ ok: false, text: e.message || 'Xatolik yuz berdi' })
    } finally {
      setSaving(false)
    }
  }

  const handleCancel = () => {
    setShowForm(false)
    setName('')
    setAddress('')
    setMsg(null)
  }

  if (err) return <div className="error-box">Xatolik yuz berdi</div>
  if (!rows) return <div className="loading">Yuklanmoqda…</div>

  return (
    <div>
      <div className="page-head">
        <h2 className="page-title">🏪 Do'konlar</h2>
        <input className="search" placeholder="🔍 Qidirish…" value={q} onChange={(e) => setQ(e.target.value)} />
        <button className="export-btn" onClick={exportCsv}>⬇️ CSV</button>
        <button className="btn btn-primary" onClick={() => { setShowForm((v) => !v); setMsg(null) }}>
          ➕ Do'kon qo'shish
        </button>
      </div>

      {showForm && (
        <div className="panel">
          <div className="form-grid">
            <div className="field">
              <label>Nomi</label>
              <input
                type="text"
                required
                placeholder="Do'kon nomi"
                value={name}
                onChange={(e) => setName(e.target.value)}
              />
            </div>
            <div className="field">
              <label>Manzil</label>
              <input
                type="text"
                placeholder="Manzil (ixtiyoriy)"
                value={address}
                onChange={(e) => setAddress(e.target.value)}
              />
            </div>
          </div>
          <div className="actions">
            <button className="btn btn-green" onClick={handleSave} disabled={saving || !name.trim()}>
              {saving ? 'Saqlanmoqda…' : 'Saqlash'}
            </button>
            <button className="btn btn-gray" onClick={handleCancel} disabled={saving}>
              Bekor
            </button>
          </div>
        </div>
      )}

      {msg && (
        <div className={msg.ok ? 'msg-ok' : 'msg-err'}>{msg.text}</div>
      )}

      {filtered.length === 0 ? (
        <p className="empty">Do'kon yo'q</p>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr><th>ID</th><th>Nomi</th><th>Manzil</th><th>A'zolar</th><th>Jami qarz</th></tr>
            </thead>
            <tbody>
              {filtered.map((s) => (
                <tr key={s.id}>
                  <td>{s.id}</td>
                  <td>{s.name}</td>
                  <td>{s.address}</td>
                  <td>{s.memberCount}</td>
                  <td>{s.totalDebt}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
