import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { logout } from '../api'

export default function Layout({ onLogout }) {
  const navigate = useNavigate()

  const handleLogout = async () => {
    try {
      await logout()
    } catch (e) {
      /* ignore */
    }
    onLogout?.()
    navigate('/login')
  }

  const linkClass = ({ isActive }) => 'nav-link' + (isActive ? ' active' : '')

  return (
    <div className="app">
      <nav className="navbar">
        <div className="nav-left">
          <span className="brand">💼 QarzBot</span>
          <NavLink to="/" className={linkClass} end>📊 Boshqaruv</NavLink>
          <NavLink to="/shops" className={linkClass}>🏪 Do'konlar</NavLink>
          <NavLink to="/users" className={linkClass}>👥 Foydalanuvchilar</NavLink>
          <NavLink to="/debts" className={linkClass}>📋 Qarzlar</NavLink>
          <NavLink to="/requests" className={linkClass}>📥 So'rovlar</NavLink>
          <NavLink to="/broadcast" className={linkClass}>📢 Xabar</NavLink>
        </div>
        <button className="logout-btn" onClick={handleLogout}>🚪 Chiqish</button>
      </nav>
      <main className="content">
        <Outlet />
      </main>
    </div>
  )
}
