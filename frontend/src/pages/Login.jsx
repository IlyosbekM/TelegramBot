import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { login } from '../api'

export default function Login({ onLogin }) {
  const [password, setPassword] = useState('')
  const [error, setError] = useState(false)
  const [loading, setLoading] = useState(false)
  const navigate = useNavigate()

  const submit = async (e) => {
    e.preventDefault()
    setError(false)
    setLoading(true)
    try {
      await login(password)
      onLogin?.()
      navigate('/')
    } catch (err) {
      setError(true)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="login-wrap">
      <form className="login-card" onSubmit={submit}>
        <h1>💼 QarzBot</h1>
        <p className="subtitle">Boshqaruv paneli</p>
        <input
          type="password"
          placeholder="Parol"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          autoFocus
        />
        {error && <div className="error">Parol noto'g'ri</div>}
        <button type="submit" disabled={loading}>
          {loading ? 'Kirilmoqda…' : 'Kirish'}
        </button>
      </form>
    </div>
  )
}
