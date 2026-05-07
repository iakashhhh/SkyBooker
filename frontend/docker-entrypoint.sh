#!/bin/sh
set -eu

GOOGLE_CLIENT_ID_ESCAPED="$(printf '%s' "${GOOGLE_CLIENT_ID:-}" | sed 's/[\/&]/\\&/g')"
sed "s/\${GOOGLE_CLIENT_ID}/${GOOGLE_CLIENT_ID_ESCAPED}/g" \
  /usr/share/nginx/html/env.template.js > /usr/share/nginx/html/env.js

exec nginx -g 'daemon off;'
