#!/usr/bin/env bash
set -Eeuo pipefail

APP_DIR="${APP_DIR:-$HOME/skybooker}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
DEPLOY_TAG="${DEPLOY_TAG:-}"
ROLLBACK_TAG_FILE="${ROLLBACK_TAG_FILE:-.last_successful_docker_tag}"
PULL_PARALLEL_LIMIT="${PULL_PARALLEL_LIMIT:-4}"
WAIT_TIMEOUT_SECONDS="${WAIT_TIMEOUT_SECONDS:-300}"

if [[ -z "${DEPLOY_TAG}" ]]; then
  echo "ERROR: DEPLOY_TAG is required (example: sha-<commit>)."
  exit 1
fi

cd "${APP_DIR}"

if [[ ! -f "${COMPOSE_FILE}" ]]; then
  echo "ERROR: Compose file not found: ${APP_DIR}/${COMPOSE_FILE}"
  exit 1
fi

touch .env

current_tag="$(grep -E '^DOCKER_IMAGE_TAG=' .env | tail -n1 | cut -d= -f2- || true)"
if [[ -n "${current_tag}" ]]; then
  echo "${current_tag}" > "${ROLLBACK_TAG_FILE}"
elif [[ ! -f "${ROLLBACK_TAG_FILE}" ]]; then
  echo "latest" > "${ROLLBACK_TAG_FILE}"
fi
rollback_tag="$(cat "${ROLLBACK_TAG_FILE}")"

set_env_var() {
  local key="$1"
  local value="$2"
  if grep -qE "^${key}=" .env; then
    sed -i.bak "s|^${key}=.*|${key}=${value}|" .env
  else
    printf "%s=%s\n" "${key}" "${value}" >> .env
  fi
}

rollback() {
  echo "Deployment failed. Rolling back to tag: ${rollback_tag}"
  set_env_var "DOCKER_IMAGE_TAG" "${rollback_tag}"
  export DOCKER_IMAGE_TAG="${rollback_tag}"
  export COMPOSE_PARALLEL_LIMIT="${PULL_PARALLEL_LIMIT}"
  docker compose -f "${COMPOSE_FILE}" pull || true
  docker compose -f "${COMPOSE_FILE}" up -d --remove-orphans --wait --wait-timeout "${WAIT_TIMEOUT_SECONDS}" || true
  docker compose -f "${COMPOSE_FILE}" ps || true
}

trap 'rollback' ERR

echo "Deploying tag: ${DEPLOY_TAG}"
set_env_var "DOCKER_IMAGE_TAG" "${DEPLOY_TAG}"
export DOCKER_IMAGE_TAG="${DEPLOY_TAG}"
export COMPOSE_PARALLEL_LIMIT="${PULL_PARALLEL_LIMIT}"

docker compose -f "${COMPOSE_FILE}" config >/dev/null
docker compose -f "${COMPOSE_FILE}" pull
docker compose -f "${COMPOSE_FILE}" up -d --remove-orphans --wait --wait-timeout "${WAIT_TIMEOUT_SECONDS}"
docker compose -f "${COMPOSE_FILE}" ps

echo "Running smoke checks..."

# Compose --wait already checks container health; these checks validate user-facing routing.
site_domain="$(grep -E '^SITE_DOMAIN=' .env | tail -n1 | cut -d= -f2- || true)"
if [[ -n "${site_domain}" ]]; then
  curl -fkLsS --retry 5 --retry-delay 3 --retry-all-errors "https://${site_domain}/" >/dev/null
  curl -fkLsS --retry 5 --retry-delay 3 --retry-all-errors "https://${site_domain}/api/actuator/health" | grep -q "\"status\":\"UP\""
else
  echo "SITE_DOMAIN not found in .env, skipping HTTP smoke checks (container health already validated by compose --wait)."
fi

echo "${DEPLOY_TAG}" > "${ROLLBACK_TAG_FILE}"
rm -f .env.bak

docker image prune -f >/dev/null 2>&1 || true
echo "Deployment successful for tag: ${DEPLOY_TAG}"
