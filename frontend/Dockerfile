# ============================================================
#  QarzBot web admin — RUNTIME image (oldindan yig'ilgan dist/ dan).
#
#  Xuddi backend kabi: korporativ TLS proxy ortida konteyner ichida
#  npm install ishlamaydi, shuning uchun dist/ HOST'da yig'iladi:
#      npm run build        (frontend/ papkasida)
#  Bu image faqat tayyor statik fayllarni nginx bilan tarqatadi.
# ============================================================
FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY dist/ /usr/share/nginx/html/

EXPOSE 80
