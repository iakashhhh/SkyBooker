#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

if [[ -z "${SONAR_TOKEN:-}" ]]; then
  echo "ERROR: SONAR_TOKEN is not set."
  echo "Usage: SONAR_TOKEN=<your_token> ./run-sonar.sh"
  exit 1
fi

SCAN_TOKEN="${SONAR_TOKEN}"
unset SONAR_TOKEN

WRAPPER="backend/admin-server/mvnw"

ensure_sonarqube_ready() {
  if docker ps --format '{{.Names}}' | grep -qx 'sonarqube'; then
    echo "ERROR: A standalone container named 'sonarqube' is already running."
    echo "Stop/remove it first so docker-compose SonarQube can use port 9000:"
    echo "  docker stop sonarqube && docker rm sonarqube"
    exit 1
  fi

  echo "==> Starting SonarQube (PostgreSQL-backed) via docker compose"
  docker compose up -d sonardb sonarqube

  echo "==> Waiting for SonarQube to become healthy"
  for _ in $(seq 1 120); do
    if curl -s http://localhost:9000/api/system/status | grep -q '"status":"UP"'; then
      echo "==> SonarQube is UP"
      return 0
    fi
    sleep 2
  done

  echo "ERROR: SonarQube did not become ready in time."
  echo "Check logs with: docker compose logs --tail=200 sonarqube sonardb"
  exit 1
}

ensure_sonarqube_ready

echo "==> Running backend tests and JaCoCo for all services"
for pom in backend/*/pom.xml; do
  service_name="$(basename "$(dirname "$pom")")"
  echo "----> ${service_name}"
  "$WRAPPER" -q -f "$pom" clean verify
done

echo "==> Verifying backend JaCoCo XML reports"
for service_dir in backend/*; do
  service_name="$(basename "$service_dir")"
  report_path="${service_dir}/target/site/jacoco/jacoco.xml"
  if [[ ! -f "$report_path" ]]; then
    echo "ERROR: Missing JaCoCo report for ${service_name}: ${report_path}"
    exit 1
  fi
done

echo "==> Running frontend tests with LCOV coverage"
(
  cd frontend
  npm test -- --code-coverage --watch=false
)

if [[ ! -f "frontend/coverage/lcov.info" ]]; then
  echo "ERROR: Missing LCOV report: frontend/coverage/lcov.info"
  exit 1
fi

echo "==> Running SonarScanner"
sonar-scanner \
  -Dsonar.projectKey=SkyBooker \
  -Dsonar.host.url=http://localhost:9000 \
  -Dsonar.login="${SCAN_TOKEN}"

echo "==> Done. Open http://localhost:9000 and check project: SkyBooker"
