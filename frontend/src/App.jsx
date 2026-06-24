import { useEffect, useState } from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'
import { getMe } from './api'
import Layout from './components/Layout'
import Login from './pages/Login'
import Dashboard from './pages/Dashboard'
import Shops from './pages/Shops'
import Users from './pages/Users'
import Debts from './pages/Debts'
import Requests from './pages/Requests'
import Broadcast from './pages/Broadcast'

export default function App() {
  const [auth, setAuth] = useState(null) // null = tekshirilmoqda, true/false

  useEffect(() => {
    getMe()
      .then(() => setAuth(true))
      .catch(() => setAuth(false))
  }, [])

  if (auth === null) {
    return <div className="loading">Yuklanmoqda…</div>
  }

  return (
    <Routes>
      <Route path="/login" element={<Login onLogin={() => setAuth(true)} />} />
      {auth ? (
        <Route element={<Layout onLogout={() => setAuth(false)} />}>
          <Route path="/" element={<Dashboard />} />
          <Route path="/shops" element={<Shops />} />
          <Route path="/users" element={<Users />} />
          <Route path="/debts" element={<Debts />} />
          <Route path="/requests" element={<Requests />} />
          <Route path="/broadcast" element={<Broadcast />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      ) : (
        <Route path="*" element={<Navigate to="/login" replace />} />
      )}
    </Routes>
  )
}
