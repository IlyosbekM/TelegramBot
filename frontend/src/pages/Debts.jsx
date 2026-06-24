import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  getDebts, getShops, getShopClients,
  createDebt, payDebt, increaseDebt, editDebt, deleteDebt, remindDebt,
} from '../api'

const statusClass = (s) => 'badge badge-' + String(s).toLowerCase()

/* ── helpers ── */
const EMPTY_CREATE = { shopId: '', clientId: '', amount: '', description: '', dueDate: '' }
const EMPTY_EDIT   = { amount: '', description: '', dueDate: '' }

export default function Debts() {
  const [rows, setRows]   = useState(null)
  const [err,  setErr]    = useState(false)
  const [q,    setQ]      = useState('')
  const [msg,  setMsg]    = useState(null)   // { ok: bool, text: string }
  const navigate = useNavigate()

  /* ── create-debt modal state ── */
  const [showCreate, setShowCreate] = useState(false)
  const [shops,      setShops]      = useState([])
  const [clients,    setClients]    = useState([])
  const [createForm, setCreateForm] = useState(EMPTY_CREATE)
  const [createErr,  setCreateErr]  = useState('')
  const [creating,   setCreating]   = useState(false)

  /* ── edit-debt modal state ── */
  const [editTarget, setEditTarget] = useState(null)   // debt object or null
  const [editForm,   setEditForm]   = useState(EMPTY_EDIT)
  const [editErr,    setEditErr]    = useState('')
  const [editing,    setEditing]    = useState(false)

  /* ── data loader ── */
  const load = () => {
    setErr(false)
    return getDebts()
      .then(setRows)
      .catch((e) => {
        if (e.status === 401) navigate('/login')
        else setErr(true)
      })
  }

  useEffect(() => { load() }, [])   // eslint-disable-line react-hooks/exhaustive-deps

  /* ── flash message helpers ── */
  const flash = (ok, text) => {
    setMsg({ ok, text })
    setTimeout(() => setMsg(null), 4000)
  }
  const handle401 = (e) => {
    if (e.status === 401) { navigate('/login'); return true }
    return false
  }

  /* ── search filter ── */
  const filtered = useMemo(() => {
    if (!rows) return []
    const s = q.trim().toLowerCase()
    if (!s) return rows
    return rows.filter(
      (d) =>
        String(d.id).includes(s) ||
        (d.clientName || '').toLowerCase().includes(s) ||
        (d.shopName   || '').toLowerCase().includes(s) ||
        (d.status     || '').toLowerCase().includes(s)
    )
  }, [rows, q])

  const exportCsv = () => { window.location.href = '/api/export/debts.csv' }

  /* ══════════════════════════════════════════
     CREATE DEBT
  ══════════════════════════════════════════ */
  const openCreate = () => {
    setCreateForm(EMPTY_CREATE)
    setCreateErr('')
    setClients([])
    setShowCreate(true)
    getShops()
      .then(setShops)
      .catch((e) => { if (!handle401(e)) setCreateErr('Do\'konlar yuklanmadi') })
  }
  const closeCreate = () => setShowCreate(false)

  const onShopChange = (shopId) => {
    setCreateForm((f) => ({ ...f, shopId, clientId: '' }))
    setClients([])
    if (!shopId) return
    getShopClients(shopId)
      .then(setClients)
      .catch((e) => { if (!handle401(e)) setCreateErr('Mijozlar yuklanmadi') })
  }

  const submitCreate = async () => {
    if (!createForm.shopId)   { setCreateErr('Do\'kon tanlang');  return }
    if (!createForm.clientId) { setCreateErr('Mijoz tanlang');    return }
    if (!createForm.amount)   { setCreateErr('Summa kiriting');   return }
    setCreating(true)
    setCreateErr('')
    try {
      await createDebt({
        shopId:      createForm.shopId,
        clientId:    createForm.clientId,
        amount:      createForm.amount,
        description: createForm.description,
        dueDate:     createForm.dueDate,
      })
      closeCreate()
      flash(true, '✅ Qarz qo\'shildi')
      load()
    } catch (e) {
      if (!handle401(e)) setCreateErr(e.message || 'Xatolik yuz berdi')
    } finally {
      setCreating(false)
    }
  }

  /* ══════════════════════════════════════════
     ROW ACTIONS
  ══════════════════════════════════════════ */
  const doPayDebt = async (d) => {
    const a = window.prompt('To\'lov summasi:')
    if (!a) return
    try {
      await payDebt(d.id, a, null)
      flash(true, '✅ To\'lov amalga oshirildi')
      load()
    } catch (e) {
      if (!handle401(e)) flash(false, e.message || 'Xatolik')
    }
  }

  const doIncrease = async (d) => {
    const a = window.prompt('Qo\'shiladigan summa:')
    if (!a) return
    try {
      await increaseDebt(d.id, a, null)
      flash(true, '✅ Qarz oshirildi')
      load()
    } catch (e) {
      if (!handle401(e)) flash(false, e.message || 'Xatolik')
    }
  }

  const openEdit = (d) => {
    setEditTarget(d)
    setEditForm(EMPTY_EDIT)
    setEditErr('')
  }
  const closeEdit = () => setEditTarget(null)

  const submitEdit = async () => {
    if (!editTarget) return
    setEditing(true)
    setEditErr('')
    const payload = {}
    if (editForm.amount)      payload.amount      = editForm.amount
    if (editForm.description) payload.description = editForm.description
    if (editForm.dueDate)     payload.dueDate     = editForm.dueDate
    try {
      await editDebt(editTarget.id, payload)
      closeEdit()
      flash(true, '✅ Qarz yangilandi')
      load()
    } catch (e) {
      if (!handle401(e)) setEditErr(e.message || 'Xatolik yuz berdi')
    } finally {
      setEditing(false)
    }
  }

  const doDelete = async (d) => {
    if (!window.confirm('Qarz o\'chirilsinmi?')) return
    try {
      await deleteDebt(d.id)
      flash(true, '✅ Qarz o\'chirildi')
      load()
    } catch (e) {
      if (!handle401(e)) flash(false, e.message || 'Xatolik')
    }
  }

  const doRemind = async (d) => {
    try {
      await remindDebt(d.id)
      flash(true, '✅ Eslatma yuborildi')
    } catch (e) {
      if (!handle401(e)) flash(false, e.message || 'Xatolik')
    }
  }

  /* ══════════════════════════════════════════
     RENDER
  ══════════════════════════════════════════ */
  if (err)   return <div className="error-box">Xatolik yuz berdi</div>
  if (!rows) return <div className="loading">Yuklanmoqda…</div>

  return (
    <div>
      {/* ── flash messages ── */}
      {msg && (
        <div className={msg.ok ? 'msg-ok' : 'msg-err'}>{msg.text}</div>
      )}

      {/* ── page header ── */}
      <div className="page-head">
        <h2 className="page-title">📋 Qarzlar</h2>
        <input
          className="search"
          placeholder="🔍 Qidirish…"
          value={q}
          onChange={(e) => setQ(e.target.value)}
        />
        <button className="export-btn" onClick={exportCsv}>⬇️ CSV</button>
        <button className="btn btn-primary" onClick={openCreate}>➕ Qarz qo'shish</button>
      </div>

      {/* ── debt table ── */}
      {filtered.length === 0 ? (
        <p className="empty">Faol qarz yo'q</p>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>#</th><th>Mijoz</th><th>Do'kon</th><th>Umumiy</th>
                <th>To'langan</th><th>Qoldiq</th><th>Holat</th><th>Muddat</th>
                <th>Amal</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((d) => (
                <tr key={d.id}>
                  <td>{d.id}</td>
                  <td>{d.clientName}</td>
                  <td>{d.shopName}</td>
                  <td>{d.total}</td>
                  <td>{d.paid}</td>
                  <td>{d.remaining}</td>
                  <td><span className={statusClass(d.status)}>{d.status}</span></td>
                  <td>{d.dueDate}</td>
                  <td>
                    <div className="actions">
                      <button className="btn btn-sm btn-green"   onClick={() => doPayDebt(d)}>💵 To'lov</button>
                      <button className="btn btn-sm btn-amber"   onClick={() => doIncrease(d)}>➕ Oshirish</button>
                      <button className="btn btn-sm btn-primary" onClick={() => openEdit(d)}>✏️ Tahrir</button>
                      <button className="btn btn-sm btn-danger"  onClick={() => doDelete(d)}>🗑</button>
                      <button className="btn btn-sm btn-light"   onClick={() => doRemind(d)}>⏰</button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* ══════════════════════════════════════════
          CREATE DEBT MODAL
      ══════════════════════════════════════════ */}
      {showCreate && (
        <div className="modal-overlay">
          <div className="modal">
            <h3>Yangi qarz</h3>

            {createErr && <div className="msg-err">{createErr}</div>}

            <div className="form-grid">
              {/* Do'kon */}
              <div className="field">
                <label>Do'kon</label>
                <select
                  value={createForm.shopId}
                  onChange={(e) => onShopChange(e.target.value)}
                >
                  <option value="">— tanlang —</option>
                  {shops.map((s) => (
                    <option key={s.id} value={s.id}>{s.name}</option>
                  ))}
                </select>
              </div>

              {/* Mijoz */}
              <div className="field">
                <label>Mijoz</label>
                {createForm.shopId && clients.length === 0 ? (
                  <p className="msg-err" style={{ margin: 0 }}>Bu do'konda bog'langan mijoz yo'q</p>
                ) : (
                  <select
                    value={createForm.clientId}
                    onChange={(e) => setCreateForm((f) => ({ ...f, clientId: e.target.value }))}
                    disabled={!createForm.shopId}
                  >
                    <option value="">— tanlang —</option>
                    {clients.map((c) => (
                      <option key={c.telegramId} value={c.telegramId}>{c.fullName}</option>
                    ))}
                  </select>
                )}
              </div>

              {/* Summa */}
              <div className="field">
                <label>Summa *</label>
                <input
                  type="text"
                  inputMode="decimal"
                  value={createForm.amount}
                  onChange={(e) => setCreateForm((f) => ({ ...f, amount: e.target.value }))}
                  placeholder="masalan: 50000"
                />
              </div>

              {/* Izoh */}
              <div className="field">
                <label>Izoh</label>
                <input
                  type="text"
                  value={createForm.description}
                  onChange={(e) => setCreateForm((f) => ({ ...f, description: e.target.value }))}
                  placeholder="ixtiyoriy"
                />
              </div>

              {/* Muddat */}
              <div className="field">
                <label>Muddat</label>
                <input
                  type="text"
                  value={createForm.dueDate}
                  onChange={(e) => setCreateForm((f) => ({ ...f, dueDate: e.target.value }))}
                  placeholder="dd.MM.yyyy"
                />
              </div>
            </div>

            <div className="modal-actions">
              <button className="btn btn-green" onClick={submitCreate} disabled={creating}>
                {creating ? 'Saqlanmoqda…' : 'Saqlash'}
              </button>
              <button className="btn btn-gray" onClick={closeCreate}>Bekor</button>
            </div>
          </div>
        </div>
      )}

      {/* ══════════════════════════════════════════
          EDIT DEBT MODAL
      ══════════════════════════════════════════ */}
      {editTarget && (
        <div className="modal-overlay">
          <div className="modal">
            <h3>Qarz #{editTarget.id} tahrir</h3>

            {editErr && <div className="msg-err">{editErr}</div>}

            <div className="form-grid">
              <div className="field">
                <label>Summa</label>
                <input
                  type="text"
                  inputMode="decimal"
                  value={editForm.amount}
                  onChange={(e) => setEditForm((f) => ({ ...f, amount: e.target.value }))}
                  placeholder="o'zgartirmaslik uchun bo'sh qoldiring"
                />
              </div>

              <div className="field">
                <label>Izoh</label>
                <input
                  type="text"
                  value={editForm.description}
                  onChange={(e) => setEditForm((f) => ({ ...f, description: e.target.value }))}
                  placeholder="o'zgartirmaslik uchun bo'sh qoldiring"
                />
              </div>

              <div className="field">
                <label>Muddat</label>
                <input
                  type="text"
                  value={editForm.dueDate}
                  onChange={(e) => setEditForm((f) => ({ ...f, dueDate: e.target.value }))}
                  placeholder="dd.MM.yyyy"
                />
              </div>
            </div>

            <div className="modal-actions">
              <button className="btn btn-green" onClick={submitEdit} disabled={editing}>
                {editing ? 'Saqlanmoqda…' : 'Saqlash'}
              </button>
              <button className="btn btn-gray" onClick={closeEdit}>Bekor</button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
