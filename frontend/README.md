# QarzBot Admin Panel (React + Vite)

QarzBot backend (Spring Boot) uchun web boshqaruv paneli.

## Ishga tushirish

1. **Backend** (loyiha ildizida, 8080-port):
   ```bash
   mvn spring-boot:run
   ```

2. **Frontend** (shu `frontend/` papkada, 5173-port):
   ```bash
   npm install
   npm run dev
   ```

3. Brauzerda oching: http://localhost:5173

   Parol — `application.yml` dagi `web.admin.password` (default: **admin123**).
   Uni o'zgartirish uchun `WEB_ADMIN_PASSWORD` environment o'zgaruvchisini bering.

## Tuzilma
- `src/api.js` — REST API chaqiruvlari (session cookie bilan).
- `src/App.jsx` — routerlar + auth guard (`/api/me`).
- `src/components/Layout.jsx` — navigatsiya.
- `src/pages/` — Login, Dashboard, Shops, Users, Debts.

Dev rejimida `/api` so'rovlari Vite proxy orqali backendga (8080) yo'naltiriladi.
