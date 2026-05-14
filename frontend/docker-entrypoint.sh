#!/bin/sh
set -eu

GOOGLE_CLIENT_ID_ESCAPED="$(printf '%s' "${GOOGLE_CLIENT_ID:-}" | sed 's/[\/&]/\\&/g')"
API_BASE_URL_ESCAPED="$(printf '%s' "${API_BASE_URL:-http://localhost:8080}" | sed 's/[\/&]/\\&/g')"
sed "s/\${GOOGLE_CLIENT_ID}/${GOOGLE_CLIENT_ID_ESCAPED}/g" \
  /usr/share/nginx/html/env.template.js | \
  sed "s/\${API_BASE_URL}/${API_BASE_URL_ESCAPED}/g" > /usr/share/nginx/html/env.js

exec nginx -g 'daemon off;'
