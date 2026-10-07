#!/bin/bash
# Monta o static/ do auth-service a partir do build do Vite (web/dist):
# - o mesmo bundle serve /admin/, /mfa/, /auth/admin/, /auth/mfa/
# - base './' + assets duplicados em cada pasta (paths relativos aninhados)
set -e
SRC="${1:-/home/euripedes/auth-frontend/dist}"
DST="${2:-/home/euripedes/auth-service/src/main/resources/static}"
rm -rf "$DST/admin" "$DST/mfa" "$DST/assets"
mkdir -p "$DST/admin" "$DST/mfa"
cp "$SRC/index.html" "$DST/admin/index.html"
cp "$SRC/index.html" "$DST/mfa/index.html"
cp -r "$SRC/assets" "$DST/assets"
cp "$SRC"/favicon.svg "$SRC"/icons.svg "$DST/admin/" "$DST/mfa/" 2>/dev/null || true
cp -r "$DST/assets" "$DST/admin/assets"
cp -r "$DST/assets" "$DST/mfa/assets"
echo "static montado em $DST"
