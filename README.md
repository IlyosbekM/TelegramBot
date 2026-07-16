# QarzBot — Do'kon Qarz Hisob-kitob Telegram Boti

Do'konlarda sotuvchi va klient o'rtasidagi qarz munosabatlarini hisoblash uchun mo'ljallangan Telegram bot.

## Imkoniyatlar

- **3 xil rol**: Admin, Sotuvchi, Klient
- **🔒 Kirish nazorati (xavfsizlik)**:
  - Mijoz do'konga **bog'lanish so'rovi** yuboradi → sotuvchi **qabul/rad** qiladi (`ShopMembership`)
  - Sotuvchi faqat **o'z do'koniga bog'langan** mijozlarni ko'radi va ular bilan ishlaydi (ma'lumot izolyatsiyasi)
  - Har bir qarz amali sotuvchining do'koniga tegishliligi tekshiriladi
- **💵 Tasdiqli to'lov**: mijoz to'lov so'rovi yuboradi → sotuvchi tasdiqlaydi → qarzdan ayiriladi
- **To'liq qarz hisob-kitobi**: qarz qo'shish, oshirish, tahrirlash, o'chirish
- **Qidiruv va hisobot**: do'kon qidirish (ID/nom), mijoz bo'yicha qidirish, to'liq statement, TOP qarzdorlar
- **🧾 Audit log**: har bir qarz amali (kim, qachon, qancha) tarixi
- **Avtomatik eslatmalar**: muddati yaqinlashgan/o'tgan qarzlar — mijozga va sotuvchiga
  - **⚙️ Mijoz sozlamasi**: mijoz avtomatik eslatmalarni o'zi **yoqishi/o'chirishi** mumkin
- **🧾 Avtomatik chek (receipt)**: yangi qarz va to'lovda mijozga toza formatlangan chek yuboriladi (screenshot/forward uchun)
- **📈 Sotuvchi statistikasi**: shu oygi yangi qarzlar, yig'ilgan to'lovlar, faol/muddati o'tgan qarzlar, TOP-5 qarzdor
- **ℹ️ Yordam tizimi**: har bir rol uchun menyudagi "ℹ️ Yordam" tugmasi orqali kontekstli qo'llanma
- **Bildirishnomalar**: bog'lanish, qarz, to'lov holatlari bo'yicha avtomatik xabar

### 🌍 Ko'p tillilik
- **3 til**: O'zbek / Rus / Ingliz (uz/ru/en). `/language` (yoki `/til`) buyrug'i orqali istalgan vaqtda almashtiriladi. Menyular, xabarlar, cheklar — barchasi foydalanuvchi tilida.
- **Ko'p valyuta**: har bir do'kon o'z valyutasini belgilaydi (UZS/USD/RUB), summalar shunga mos formatlanadi.

### 🚀 Ilg'or imkoniyatlar (investor-grade)
- **📈 Analitika**: undirilish darajasi (collection rate), kechikish (aging) tahlili 4 guruhda — ASCII grafik bilan, shu oy faoliyati, eng katta qarzdorlar TOP-5 (sotuvchi + admin uchun umumiy).
- **⭐ Ishonch bali (Trust score)**: mijozning to'lov tarixiga qarab 0–100 ball, reyting va **tavsiya etilgan kredit limiti** (fintech).
- **🏆 Reyting (Leaderboard)**: eng ishonchli mijozlar va eng katta qarzdorlar (🥇🥈🥉 medallar bilan).
- **📅 Bo'lib to'lash (Installments)**: qarzni N ta bo'lakka jadval bilan bo'lish, har bir bo'lakni "to'landi" deb belgilash.
- **🧾 PDF cheklar/hisobotlar**: qarz cheki va to'liq hisobot PDF holida yuboriladi (OpenPDF).
- **📷 QR kod**: qarz ma'lumotini QR kod rasmi qilib ulashish (ZXing).
- **🔎 Kengaytirilgan qidiruv/filtr**: faol / muddati o'tgan / yirik qarzlar bo'yicha tezkor filtr + ism bo'yicha qidiruv.
- **🤝 To'lov va'dasi**: mijoz qarzni to'lash sanasini va'da qiladi — sotuvchilar xabardor bo'ladi, bot va'da kunida mijozga eslatadi, ertasiga bajarilgan/buzilganini avtomatik aniqlab (KEPT/BROKEN) ikkala tomonga xabar beradi.
- **⚠️ E'tiroz (dispute)**: mijoz noto'g'ri deb hisoblagan qarziga sabab yozib e'tiroz bildiradi — sotuvchi qabul qiladi (so'ng qarzni tahrirlaydi) yoki sabab ko'rsatib rad etadi; hammasi audit tarixida.
- **↩️ To'lovni bekor qilish**: sotuvchi xato kiritilgan OXIRGI to'lovni 24 soat ichida, tasdiq bosqichi bilan bekor qiladi — qoldiq/holat qayta hisoblanadi, mijozga xabar boradi, audit yoziladi.
- **📥 Excel eksport**: sotuvchi uchun 3 varaqli .xlsx (Qarzlar / Mijozlar / To'lovlar 90 kun), admin uchun barcha do'konlar bo'yicha hisobot; har dushanba 08:00 da sotuvchilarga avtomatik haftalik Excel.
- **💾 To'liq zaxira nusxa**: admin bitta tugma bilan butun bazani JSON (ZIP arxiv) ko'rinishida yuklab oladi.

## Texnologiyalar

- Java 17
- Spring Boot 3.3.5
- PostgreSQL
- TelegramBots Java API 10.0.0 (`SpringLongPollingBot`)
- Maven

## O'rnatish

### 1. PostgreSQL bazasini yarating

```sql
CREATE DATABASE qarzbot;
CREATE USER postgres WITH PASSWORD 'postgres';
GRANT ALL PRIVILEGES ON DATABASE qarzbot TO postgres;
```

### 2. Telegram bot tokenini oling

[@BotFather](https://t.me/BotFather) ga `/newbot` buyrug'ini yuboring va olingan tokenni saqlang.

### 3. Maxfiy qiymatlarni sozlang (token, admin, DB paroli)

Maxfiy qiymatlar **kodga yozilmaydi** va **git'ga ketmaydi**. Ikki yo'l bor:

**(a) Mahalliy ishlash uchun — `.secrets.yml`** (tavsiya, `.gitignore` qilingan):

```yaml
# .secrets.yml  (loyiha ildizida)
telegram:
  bot:
    token: 1234567890:ABC...      # @BotFather token
    admin-ids: 123456789           # vergul bilan ko'p admin
spring:
  datasource:
    password: "sizning-db-parolingiz"
```

`application.yml` uni avtomatik yuklaydi (`optional:file:./.secrets.yml`) — qo'shimcha flag shart emas.

**(b) Yoki environment o'zgaruvchilari orqali:**

```bash
export BOT_TOKEN="1234567890:ABC..."
export ADMIN_IDS="123456789"
export DB_PASSWORD="..."
```

> ⚠️ Token bir marta ochiq turgan bo'lsa — `@BotFather` → `/revoke` qilib **yangi token** oling.

### 4. Loyihani ishga tushiring

```bash
mvn clean install
mvn spring-boot:run
```

## 🐳 Docker bilan ishga tushirish (eng oson yo'l — BITTA buyruq)

To'liq stack (PostgreSQL + bot/REST API + web admin panel) bitta buyruq bilan:

```powershell
# Windows
.\start.ps1
```

```bash
# Linux / Mac
./start.sh
```

Skript o'zi: `.env` yo'q bo'lsa `.env.example` dan yaratadi (to'ldirishni so'raydi) → jar'ni host'da yig'adi (`mvn -o`, kerak bo'lsa online fallback) → frontend'ni yig'adi (`npm run build`) → `docker compose up -d --build`.

| Servis | Konteyner | Manzil |
|--------|-----------|--------|
| PostgreSQL 16 | `qarzbot-db` | faqat ichki tarmoq (`db:5432`) |
| Bot + REST API | `qarzbot-app` | `http://localhost:8080` |
| Web admin (nginx) | `qarzbot-web` | `http://localhost:3000` (`.env` → `WEB_PORT`) |

Boshqa buyruqlar: to'xtatish `.\start.ps1 -Down` (yoki `docker compose down`) · qayta ko'tarish (yig'masdan) `.\start.ps1 -SkipBuild` · loglar `docker compose logs -f app`

> ℹ️ Jar va frontend `dist/` **host'da** yig'iladi, Docker image'lar faqat tayyor artefaktni ishlatadi — korporativ TLS proxy ortida konteyner ichida Maven/npm yuklab ololmaydi (PKIX). Scheduler'lar mahalliy vaqtda ishlashi uchun konteynerlarga `TZ=Asia/Tashkent` beriladi (`.env` da o'zgartirsa bo'ladi). Web panel nginx orqali `/api` ni `app:8080` ga proxy qiladi — CORS/cookie muammosi yo'q.

## 🌿 Branch strategiyasi va CI/CD (GitHub Actions)

| Branch | Vazifa | Avtomatik jarayon |
|--------|--------|-------------------|
| `dev`  | Kundalik ishlanma | **CI** — backend (`mvn verify`) + frontend (`vite build`) |
| `prod` | Barqaror / релиз | **CI** + **CD** — Docker image yig'ib **GHCR**'ga joylaydi |

- **CI** (`.github/workflows/ci.yml`): `dev`/`prod` ga har push va PR da build qiladi.
- **CD** (`.github/workflows/cd.yml`): `prod` ga push'da Docker image'ni
  `ghcr.io/ilyosbekm/telegrambot:latest` ga chiqaradi (public repo uchun **bepul**,
  qo'shimcha secret shart emas — `GITHUB_TOKEN` avtomatik ishlatiladi).

### GitHub Secrets (ixtiyoriy — faqat serverga deploy uchun)

Repo → **Settings → Secrets and variables → Actions** ga qo'shing (server bo'lsa):
`DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY` — keyin `cd.yml` dagi SSH-deploy blokini oching.

> Botni faqat **bitta** instansiyada ishga tushiring (Telegram bitta tokenга bitta
> `getUpdates` ulanishiga ruxsat beradi — aks holda 409 Conflict).

## Foydalanish

### Admin
1. Botga `/start` yuboring
2. **➕ Do'kon qo'shish** — yangi do'kon yarating (nom → manzil)
3. **🏪 Do'konlar** — barcha do'konlar va faol qarzlari
4. **👥 Foydalanuvchilar** — rollar bo'yicha ro'yxat; inline tugmalar orqali
   klientni **sotuvchi qilish** (do'konga biriktirish) yoki sotuvchini **klient qilish**
5. **📊 Umumiy hisobot** — barcha do'konlar bo'yicha statistika
6. **⚙️ Sozlamalar** — bot statistikasi (foydalanuvchi/sotuvchi/klient/do'kon soni)
7. **ℹ️ Yordam** — admin menyusi bo'yicha qo'llanma

### Sotuvchi (Admin tomonidan do'konga biriktiriladi)
1. **➕ Qarz qo'shish** — bog'langan mijozlar ro'yxatidan tanlash → summa → izoh → muddat
2. **📋 Qarzlar ro'yxati** — faol qarzlar; har biri ostida inline amal tugmalari:
   - 💵 **To'lov** — naqd to'lovni qo'lda yozish · ➕ **Oshirish** — qarzga summa qo'shish
   - ✏️ **Tahrirlash** (summa/izoh/muddat) · 🗑 **O'chirish** (tasdiq bilan)
   - 📜 **Tarix** — to'lovlar tarixi · ⏰ **Eslatma** — mijozga eslatma
3. **👥 Mijozlar** — do'konga **bog'langan** mijozlar + **📊 To'liq hisobot** (statement)
4. **🔍 Qidirish** — mijoz ismi yoki telefon bo'yicha qarz izlash
5. **📥 So'rovlar** — kutilayotgan **bog'lanish** va **to'lov** so'rovlari (qabul/rad/tasdiq)
6. **📊 Hisobot** — a'zolar, faol/muddati o'tgan qarzlar, TOP qarzdorlar
7. **📈 Statistika** — shu oygi yangi qarzlar (soni+summa), yig'ilgan to'lovlar, faol/muddati o'tgan qarzlar, TOP-5 qarzdor
8. **🧾 Tarix** — amallar audit tarixi
9. **⏰ Eslatma yuborish** — barcha mijozlarga eslatma
10. **ℹ️ Yordam** — sotuvchi menyusi bo'yicha qo'llanma
> Qarz qo'shilganda va to'lov qabul qilinganda mijozga **avtomatik chek** yuboriladi.

### Klient
> Birinchi `/start` da bot **telefon raqamini** so'raydi (tugma orqali).

1. **🏪 Do'konlar** / **🔍 Do'kon qidirish** — do'konni topib **🔗 Bog'lanish** so'rovi yuborish
   (sotuvchi qabul qilgach bog'lanasiz)
2. **💳 Mening qarzlarim** — do'kon bo'yicha qarzlar, umumiy qoldiq; har qarzda **💵 To'lash**
   (summa kiritasiz → sotuvchi tasdig'idan keyin ayiriladi)
3. **📜 To'lov tarixim** — qilingan to'lovlar
4. **⚙️ Sozlamalar** — avtomatik eslatmalarni yoqish/o'chirish
5. **ℹ️ Yordam** — klient menyusi bo'yicha qo'llanma
> Yangi qarz va tasdiqlangan to'lovda sizga **avtomatik chek** keladi.

## Loyiha tuzilmasi

```
src/main/java/com/qarzbot/
├── QarzBotApplication.java       # Main
├── config/BotConfig.java          # Token/username konfiguratsiya
├── bot/QarzBot.java               # Telegram long polling bot
├── entity/                        # JPA entitylar
│   ├── BotUser.java, Shop.java, Debt.java, Payment.java, Role.java
│   ├── ShopMembership.java         # mijoz↔do'kon bog'lanishi (PENDING/ACCEPTED/REJECTED)
│   ├── PaymentRequest.java         # mijoz to'lov so'rovi (sotuvchi tasdiqlaydi)
│   ├── AuditLog.java               # amallar tarixi
│   ├── PaymentPromise.java         # mijoz to'lov va'dasi (OPEN/KEPT/BROKEN)
│   ├── Dispute.java                # qarzga e'tiroz (OPEN/ACCEPTED/REJECTED)
│   └── Product.java, Reminder.java, Installment.java
├── repository/                    # Spring Data JPA repositorylari (JOIN FETCH bilan)
├── service/                       # Business logic
│   ├── MembershipService, PaymentRequestService, AuditService
├── handler/                       # Update routing va role handlerlar
│   ├── UpdateRouter.java
│   ├── AdminHandler.java
│   ├── SellerHandler.java
│   ├── ClientHandler.java
│   └── ConversationHandler.java   # FSM (multi-step muloqot)
├── scheduler/ReminderScheduler.java
└── util/                          # KeyboardFactory, MessageFormatter
```

## Eslatma rejimi (schedulerlar)

- **08:30** — muddati o'tgan ACTIVE qarzlar avtomatik OVERDUE holatiga o'tkaziladi
- **09:00** — muddati yaqinlashgan (3 kun ichida) va o'tgan qarzlar uchun avtomatik eslatma
- **09:15** — bugun va'da qilingan to'lovlar bo'yicha mijozlarga eslatma
- **09:45** — muddati o'tgan va'dalar KEPT/BROKEN deb baholanadi, ikkala tomonga xabar
- **20:00** — sotuvchilarga kunlik xulosa
- **Dushanba 08:00** — sotuvchilarga haftalik Excel hisobot
- Mijoz **⚙️ Sozlamalar** orqali avto-eslatmani o'chirib qo'ysa, unga eslatma yuborilmaydi (lekin muddati o'tganda sotuvchiga baribir xabar boradi)
- Har **5 daqiqada** rejalashtirilgan eslatmalar yuboriladi

## Keyingi qadamlar (TODO)

- [ ] Liquibase/Flyway migratsiyalari
- [x] ~~PDF eksport~~ — qo'shildi (PDF chek + hisobot, OpenPDF)
- [x] ~~Ko'p tilli interfeys~~ — qo'shildi (UZ/RU/EN, `/language`)
- [x] ~~Avtomatik chek~~ — matnli + PDF chek qo'shildi
- [x] ~~Sotuvchi statistikasi~~ — to'liq analitika (aging, collection rate, top debtors) qo'shildi
- [x] ~~Bo'lib to'lash, Trust score, QR, Leaderboard, kengaytirilgan filtr~~ — qo'shildi
- [x] ~~Excel eksport~~ — qo'shildi (bot ichida .xlsx + haftalik avtomatik hisobot)
- [x] ~~To'lov va'dasi, e'tiroz (dispute), to'lovni bekor qilish, to'liq zaxira nusxa~~ — qo'shildi
- [ ] Foiz hisoblash (qarz uzaytirilganda)
- [ ] Push-bildirishnoma sozlamalari
