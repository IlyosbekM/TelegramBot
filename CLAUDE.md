# CLAUDE.md — QarzBot loyihasi uchun yo'riqnoma

Bu fayl Claude (AI yordamchi) uchun shu loyiha bilan ishlashda doimo amal qilishi kerak bo'lgan qoidalarni belgilaydi.

## Loyiha haqida qisqacha

**QarzBot** — do'konlarda sotuvchi va klient o'rtasidagi qarz munosabatlarini boshqaruvchi Telegram bot.

- **Til**: Java 17 (LTS)
- **Framework**: Spring Boot 3.3.5 (Hibernate 6.5 — JAXB muammosiz)
- **DB**: PostgreSQL (JPA/Hibernate)
- **Bot kutubxonasi**: TelegramBots 10.0.0 (yangi API: `SpringLongPollingBot` + `TelegramClient`)
- **Rollar**: ADMIN, SELLER, CLIENT

## 🔒 OPERATIONAL PROTOCOL (BINDING — read this first on every task)

> This block is the authoritative, machine-actionable version of the workflow.
> It is written in English on purpose. Follow it literally on **every** task.

**On every new task, do this in order:**

1. **Read the config first.** Re-read this `CLAUDE.md` at the start of each task and
   load the rules below before doing anything else. Do not act from memory alone.

2. **Refactor the user's prompt yourself (act as Opus).** The user's raw prompt may
   be short, informal, or in Uzbek. You (the orchestrator, running on Opus) must:
   - Analyze the project (relevant files, packages, entities, services, handlers).
   - Rewrite the raw prompt into a **clear, structured, technical English spec**:
     which files to touch, required method signatures, validations, transactions,
     entities/repositories, FSM states, and the tests to write.
   - This refactored spec is for **your own** understanding and for the sub-agent.

3. **Delegate coding to Sonnet sub-agent(s).** Spawn sub-agent(s) (via the `Agent`
   tool, `model: "sonnet"`) and hand them the refactored **English** prompt. The
   sub-agent writes the actual code. Keep the spec self-contained so the cold-start
   agent has everything it needs (paths, signatures, conventions from this file).

   **Scale the number of sub-agents AND the effort to the task's difficulty** (you,
   the Opus orchestrator, judge the difficulty after analyzing the project):

   | Difficulty | Sub-agents | Effort level (encode in the spec) |
   |-----------|-----------|-----------------------------------|
   | **Easy** (one method, one file, no cross-cutting change) | 1 Sonnet agent, **medium** effort | concise spec, exact signatures |
   | **Medium** (a feature touching a few files: handler + service + keyboard) | 1 Sonnet agent, **high** effort | detailed spec: every file, every case, edge cases, verify step |
   | **Hard / large** (architecture refactor, many files, patterns, risky build) | **Multiple** Sonnet agents, **high** effort | split into independent or **sequential phases** (e.g. Pass 1 foundation → Pass 2 patterns); one agent per phase; each phase must compile on its own |

   Rules for scaling:
   - "Effort" = how thorough the spec is + how much verification you demand from the
     agent (grep checks, line-count checks, "preserve every string verbatim"). Higher
     effort → more explicit constraints and a mandatory self-verification step.
   - **Prefer running multiple sub-agents in PARALLEL**, partitioning the work by
     file/package so that **no two agents ever touch the same file**. Before spawning,
     list each agent's exact file set and confirm the sets are disjoint.
   - If a change genuinely requires two agents to edit the **same** file (e.g.
     `UpdateRouter`, `QarzBot`), do NOT parallelize it — run them **sequentially** and
     feed the second agent the result of the first (or just do that shared file yourself).
   - After each agent returns, **you (Opus) spot-check** the highest-risk files before
     moving on or before telling the user to build. Never blindly trust the agent's
     "all clean" report.
   - Prefer fewer, well-specified agents over many shallow ones — each cold start
     re-derives context, which is the expensive path.

4. **Language rules (strict):**
   - **Sub-agent prompt → English.** Always.
   - **Conversation with the user / between agents → English.**
   - **User-facing bot strings, features, error messages → Uzbek (Latin).**
   - **Final result summary shown to the user → Uzbek** (what changed, which
     files, which features were added), while the working dialogue stays English.

5. **Build/run/test → user runs them.** Per the rules below, never run
   `mvn clean install` / `spring-boot:run` / `mvn test` yourself. Write the code,
   then tell the user (in Uzbek) which command to run.

```
raw prompt (user, Uzbek/short)
        │
        ▼
[Opus orchestrator]  ── analyze project + refactor → structured ENGLISH spec
        │
        ▼
[Sonnet sub-agent]   ── writes code from the English spec
        │
        ▼
result summary to user in UZBEK   (working dialogue stays ENGLISH)
```

---

## ⚠️ ASOSIY ISH JARAYONI (MUHIM!)

Bu loyihada **ikki bosqichli prompt strategiyasi** qo'llaniladi:

### 1-bosqich: Promptni Claude **Opus** da qayta yozish (refactor)

Foydalanuvchi qanday prompt yuborgan bo'lsa, ChatGPT/Claude'ga to'g'ridan-to'g'ri bermaslik kerak. Avval **Claude Opus** modeliga quyidagi vazifa beriladi:

> "Quyidagi prompt'ni Claude Sonnet uchun aniq, tuzilgan, texnik talablar bilan to'liq qilib qayta yoz. Qaysi fayllarga tegish kerak, qanday tuzilma kerak, qanday testlar kerak — barchasini batafsil tushuntir."

**Nima uchun Opus?** Opus murakkab masalalarni chuqurroq tahlil qiladi, kontekstni yaxshi tushunadi va aniqroq talablar yozadi.

### 2-bosqich: Refactor qilingan promptni Claude **Sonnet** ga berish

Opus tomonidan qayta yozilgan, tuzilgan prompt **Claude Sonnet** ga uzatiladi. Sonnet kodni amalda yozib beradi.

**Nima uchun Sonnet?** Sonnet kod yozishda tez va arzon, aniq talablarga muvofiq yaxshi natija beradi.

### Sxema

```
┌──────────────┐    1. xom prompt    ┌─────────────┐
│ Foydalanuvchi│ ──────────────────► │ Claude Opus │
└──────────────┘                     └──────┬──────┘
                                            │ 2. tuzilgan,
                                            │    aniqroq prompt
                                            ▼
                                     ┌──────────────┐
                                     │ Claude Sonnet│
                                     └──────┬───────┘
                                            │ 3. kod
                                            ▼
                                     ┌──────────────┐
                                     │   Loyiha     │
                                     └──────────────┘
```

### Misol

**Xom prompt (foydalanuvchidan):**
> "qarz qo'shadigan funksiya yoz"

**Opus tomonidan qayta yozilgan prompt:**
> "Loyihadagi `DebtService.java` ga `createDebt` metodini qo'sh. Parametrlar: `Shop shop`, `BotUser client`, `BotUser seller`, `BigDecimal amount`, `String description`, `LocalDate dueDate`. Validatsiyalar: amount > 0; client.role == CLIENT; seller.shop == shop. `@Transactional` ishlat. Yangi `Debt` qaytarsin, status=ACTIVE. Tegishli repository va entity'lar `com.qarzbot.entity` va `com.qarzbot.repository` paketlarida. Ushbu yangi metod uchun JUnit test ham yozib ber..."

**Sonnet ga beriladi va kod yoziladi.**

---

## Loyiha tuzilmasi (Claude eslab qolishi uchun)

```
src/main/java/com/qarzbot/
├── QarzBotApplication.java      — Spring Boot kirish nuqtasi
├── config/BotConfig.java         — application.yml dan token/admin id'lar
├── bot/QarzBot.java              — Telegram long polling bot
├── entity/                       — JPA entitylar
│   ├── BotUser, Shop, Debt, Payment, Product, Reminder, Role
├── repository/                   — Spring Data JPA
├── service/                      — Business logic
│   ├── UserService, ShopService, DebtService, ProductService
├── handler/
│   ├── UpdateRouter              — Update'larni rolega qarab yo'naltiradi
│   ├── AdminHandler              — Admin menyusi
│   ├── SellerHandler             — Sotuvchi menyusi
│   ├── ClientHandler             — Klient menyusi
│   └── ConversationHandler       — Ko'p bosqichli muloqot (FSM)
├── scheduler/ReminderScheduler.java — Cron eslatmalar
└── util/                         — KeyboardFactory, MessageFormatter
```

## Kod yozish qoidalari

1. **Lombok** ishlatiladi: `@Getter`, `@Setter`, `@RequiredArgsConstructor`, `@Builder`.
2. **Constructor injection** — `@Autowired` o'rniga `final` field + `@RequiredArgsConstructor`.
3. **Transactional** — har qanday DB yozadigan service metodida `@Transactional`.
4. **Til**: foydalanuvchi xabarlari va xato matnlari **o'zbek tilida** (lotin).
5. **Pul birligi**: `BigDecimal`, `MessageFormatter.money()` orqali ko'rsatiladi.
6. **FSM**: ko'p bosqichli muloqot uchun `BotUser.state` ishlatiladi (format: `"BO'LIM_HOLAT:param"`).
7. **Yangi qarz/to'lov yaratilganda mijozga avtomatik xabar** yuborilsin.

## Database o'zgarishlari

JPA `ddl-auto: update` ishlatiladi (development). Production uchun keyinroq Liquibase qo'shiladi.

## Test va ishga tushirish

```bash
mvn clean install       # build
mvn spring-boot:run     # ishga tushirish
mvn test                # testlar
```

### ⚠️ Uzoq davom etadigan jarayonlarni Claude O'ZI ishga tushirmaydi

Quyidagi uzoq davom etadigan / interaktiv jarayonlarni **Claude o'zi bajarmaydi** — ularni **foydalanuvchi o'zi qo'lda ishga tushiradi**. Claude'ning vazifasi — faqat **kodni yozib berish**:

- `mvn clean install` / `mvn clean compile` (build)
- `mvn spring-boot:run` (botni ishga tushirish)
- `mvn test` (testlar)
- shunga o'xshash boshqa uzoq yoki bloklab qo'yadigan jarayonlar

**Qoida:** Claude kod o'zgartirishini yozib bo'lgach, build/run/test'ni o'zi chaqirmaydi. Faqat "endi shu komandani ishga tushiring" deb foydalanuvchiga aytadi (masalan: `mvn clean install`). Build natijasi/xatosini foydalanuvchi qaytarsa, Claude shunga qarab kodni tuzatadi.

## Eslatma Claude uchun

- Foydalanuvchi xom prompt bersa — **Opus ga refactor uchun yo'naltirilgan promptni esla**, lekin agar foydalanuvchi to'g'ridan-to'g'ri ish qil desa, vazifani bajar.
- Yangi feature qo'shilganda README.md va shu CLAUDE.md ni yangilash kerak.
- Telegram API: `org.telegram:telegrambots-springboot-longpolling-starter` + `telegrambots-client` 10.0.0. **Eski `TelegramLongPollingBot` ishlatilmaydi** — yangi `SpringLongPollingBot` interfeysi va `OkHttpTelegramClient` ishlatiladi.

---

## Arxitektura (refactor'dan keyin — design pattern'lar)

Bot kodi GoF pattern'lar bo'yicha bo'lingan (god-class'lar yo'q):
- **Facade** — `bot/BotMessenger.java`: barcha Telegram yuborish bitta joydan (`send/sendMarkdown/reply/menu/execute/broadcast`). `TelegramClient`'ni shu egallaydi (circular dependency yo'q).
- **Command + Chain of Responsibility** — `handler/callback/`: `CallbackHandler` interfeysi + `CallbackDispatcher`. Har bir tugma (prefix) alohida `@Component` (admin/seller/client sub-paketlari).
- **State / Strategy** — `handler/conversation/`: `ConversationFlow` interfeysi + `Admin/Seller/ClientConversationFlow`. `ConversationHandler` — yupqa dispatcher (state prefiksi bo'yicha). ⚠️ Har bir flow oxirida **fallback** bor: noma'lum/qotgan state'ni tozalab menyuga qaytaradi (aks holda foydalanuvchi qamalib qoladi).
- **Presenter** — `handler/seller/SellerViewService.java`: sotuvchi hisobot/ro'yxat render'lash.
- **Factory** — `util/KeyboardFactory.java`.

## Web admin panel

Brauzer orqali boshqaruv: **backend REST API** + **alohida React frontend**.
- **Backend**: `src/main/java/com/qarzbot/web/` — `@RestController` (`/api/**`), session-auth (`WebAuthInterceptor`, 401 redirect emas), CORS (`http://localhost:5173`), CSV eksport (`ExportController` → `/api/export/*.csv`). Parol: `application.yml` → `web.admin.password` (env: `WEB_ADMIN_PASSWORD`, default `admin123`). **Thymeleaf ISHLATILMAYDI** — faqat JSON.
- **Frontend**: `frontend/` — React 18 + Vite + react-router. `npm install && npm run dev` (5173). Dev'da `/api` Vite proxy orqali 8080 ga (cookie same-origin).
- **Scheduler**: `scheduler/SummaryScheduler.java` — har kuni 20:00 da sotuvchilarga kunlik xulosa.

### ⚠️ Ishga tushirish — FAQAT BITTA instansiya
Telegram bitta tokenga faqat **bitta** `getUpdates` (long-polling) ulanishiga ruxsat beradi. Ikkita instansiya ishlasa → ikkinchisi **409 Conflict** oladi va **jim qoladi** (+ 8080 port to'qnashuvi). IntelliJ'da "Rerun" qilishdan oldin eski run'ni ⏹ **Stop** qiling yoki eski `java` jarayonini o'chiring.
