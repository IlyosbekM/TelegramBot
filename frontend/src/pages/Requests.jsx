import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  getRequests,
  approveShopReq,
  rejectShopReq,
  acceptMembership,
  rejectMembership,
  confirmPayment,
  rejectPayment,
} from '../api'

export default function Requests() {
  const navigate = useNavigate()
  const [data, setData] = useState({ shopRequests: [], memberships: [], payments: [] })
  const [loading, setLoading] = useState(true)
  const [msg, setMsg] = useState(null) // { type: 'ok'|'err', text: string }

  async function load() {
    setLoading(true)
    try {
      const result = await getRequests()
      setData(result)
    } catch (e) {
      if (e.status === 401) return navigate('/login')
      setMsg({ type: 'err', text: e.message || 'Yuklab bo\'lmadi' })
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, [])

  async function act(fn, successText) {
    setMsg(null)
    try {
      await fn()
      setMsg({ type: 'ok', text: successText || 'Muvaffaqiyatli bajarildi' })
      await load()
    } catch (e) {
      if (e.status === 401) return navigate('/login')
      setMsg({ type: 'err', text: e.message || 'Xatolik yuz berdi' })
    }
  }

  return (
    <div>
      <h2 className="page-title">📥 So'rovlar</h2>

      {msg && (
        <div className={msg.type === 'ok' ? 'msg-ok' : 'msg-err'}>
          {msg.text}
        </div>
      )}

      {loading && <div className="loading">Yuklanmoqda...</div>}

      {!loading && (
        <>
          {/* Section 1: Shop requests */}
          <div>
            <div className="section-title">
              🏪 Do'kon ochish so'rovlari
              <span className="count-pill">{data.shopRequests.length}</span>
            </div>
            {data.shopRequests.length === 0 ? (
              <div className="empty">So'rov yo'q</div>
            ) : (
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>Nomi</th>
                      <th>Manzil</th>
                      <th>Telegram ID</th>
                      <th>Amallar</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.shopRequests.map((item) => (
                      <tr key={item.id}>
                        <td>{item.name}</td>
                        <td>{item.address}</td>
                        <td>{item.requesterTelegramId}</td>
                        <td className="actions">
                          <button
                            className="btn btn-sm btn-green"
                            onClick={() =>
                              act(
                                () => approveShopReq(item.id),
                                `"${item.name}" do'koni tasdiqlandi`
                              )
                            }
                          >
                            ✅ Tasdiqlash
                          </button>
                          <button
                            className="btn btn-sm btn-danger"
                            onClick={() => {
                              const r = window.prompt('Sabab (ixtiyoriy):')
                              act(
                                () => rejectShopReq(item.id, r),
                                `"${item.name}" rad etildi`
                              )
                            }}
                          >
                            ❌ Rad etish
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          {/* Section 2: Membership requests */}
          <div>
            <div className="section-title">
              🔗 Bog'lanish so'rovlari
              <span className="count-pill">{data.memberships.length}</span>
            </div>
            {data.memberships.length === 0 ? (
              <div className="empty">So'rov yo'q</div>
            ) : (
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>Mijoz</th>
                      <th>Do'kon</th>
                      <th>Amallar</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.memberships.map((item) => (
                      <tr key={item.id}>
                        <td>{item.clientName}</td>
                        <td>{item.shopName}</td>
                        <td className="actions">
                          <button
                            className="btn btn-sm btn-green"
                            onClick={() =>
                              act(
                                () => acceptMembership(item.id),
                                `${item.clientName} qabul qilindi`
                              )
                            }
                          >
                            ✅ Qabul
                          </button>
                          <button
                            className="btn btn-sm btn-danger"
                            onClick={() =>
                              act(
                                () => rejectMembership(item.id),
                                `${item.clientName} rad etildi`
                              )
                            }
                          >
                            ❌ Rad
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>

          {/* Section 3: Payment requests */}
          <div>
            <div className="section-title">
              💵 To'lov so'rovlari
              <span className="count-pill">{data.payments.length}</span>
            </div>
            {data.payments.length === 0 ? (
              <div className="empty">So'rov yo'q</div>
            ) : (
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>Mijoz</th>
                      <th>Do'kon</th>
                      <th>Summa</th>
                      <th>Qarz #</th>
                      <th>Amallar</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.payments.map((item) => (
                      <tr key={item.id}>
                        <td>{item.clientName}</td>
                        <td>{item.shopName}</td>
                        <td>{item.amount}</td>
                        <td>(#{item.debtId})</td>
                        <td className="actions">
                          <button
                            className="btn btn-sm btn-green"
                            onClick={() =>
                              act(
                                () => confirmPayment(item.id),
                                `To'lov tasdiqlandi`
                              )
                            }
                          >
                            ✅ Tasdiqlash
                          </button>
                          <button
                            className="btn btn-sm btn-danger"
                            onClick={() =>
                              act(
                                () => rejectPayment(item.id),
                                `To'lov rad etildi`
                              )
                            }
                          >
                            ❌ Rad
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </>
      )}
    </div>
  )
}
