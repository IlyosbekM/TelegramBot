import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { getDashboard } from '../api'

const CARDS = [
  { key: 'shopCount', label: "Do'konlar", icon: '🏪' },
  { key: 'userCount', label: 'Foydalanuvchilar', icon: '👥' },
  { key: 'sellerCount', label: 'Sotuvchilar', icon: '🛒' },
  { key: 'clientCount', label: 'Mijozlar', icon: '👤' },
  { key: 'activeDebtCount', label: 'Faol qarzlar', icon: '📋' },
  { key: 'totalDebt', label: 'Jami qarz', icon: '💰' },
]

export default function Dashboard() {
  const [data, setData] = useState(null)
  const [err, setErr] = useState(false)
  const navigate = useNavigate()

  useEffect(() => {
    getDashboard()
      .then(setData)
      .catch((e) => (e.status === 401 ? navigate('/login') : setErr(true)))
  }, [navigate])

  if (err) return <div className="error-box">Xatolik yuz berdi</div>
  if (!data) return <div className="loading">Yuklanmoqda…</div>

  return (
    <div>
      <h2 className="page-title">📊 Boshqaruv paneli</h2>
      <div className="cards">
        {CARDS.map((c) => (
          <div className="card" key={c.key}>
            <div className="card-icon">{c.icon}</div>
            <div className="card-value">{data[c.key]}</div>
            <div className="card-label">{c.label}</div>
          </div>
        ))}
      </div>
    </div>
  )
}
