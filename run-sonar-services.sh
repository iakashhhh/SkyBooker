#!/usr/bin/env bash
set -uo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SONAR_URL="${SONAR_URL:-http://localhost:9000}"
SONAR_TOKEN="${SONAR_TOKEN:-}"

if [[ -z "${SONAR_TOKEN}" ]]; then
  echo "ERROR: SONAR_TOKEN is not set."
  echo "Usage: SONAR_TOKEN=<your_token> ./run-sonar-services.sh"
  exit 1
fi

BACKEND_SERVICES=(
  auth-service
  booking-service
  payment-service
  flight-service
  passenger-service
  seat-service
  notification-service
  airline-airport-service
)

# Optional infra services (run by setting INCLUDE_INFRA=true)
INFRA_SERVICES=(
  api-gateway
  eureka-server
  admin-server
)

MVNW_REL="../api-gateway/mvnw"
SUCCESSFUL_PROJECTS=()
FAILED_PROJECTS=()
SERVICE_COVERAGE=()

if [[ "${INCLUDE_INFRA:-false}" == "true" ]]; then
  BACKEND_SERVICES+=("${INFRA_SERVICES[@]}")
fi

wait_for_sonarqube() {
  echo "==> Waiting for SonarQube at ${SONAR_URL}"
  for _ in $(seq 1 120); do
    if curl -s "${SONAR_URL}/api/system/status" | grep -q '"status":"UP"'; then
      echo "==> SonarQube is UP"
      return 0
    fi
    sleep 2
  done

  echo "ERROR: SonarQube is not reachable at ${SONAR_URL}"
  exit 1
}

scan_backend_service() {
  local service="$1"
  local service_dir="${ROOT_DIR}/backend/${service}"
  local project_key="skybooker-${service}"
  local project_name="SkyBooker ${service}"

  if [[ ! -d "${service_dir}" ]]; then
    echo "SKIP: Missing directory ${service_dir}"
    return 0
  fi

  echo
  echo "====> Scanning backend service: ${service}"
  cd "${service_dir}"

  if [[ ! -x "${MVNW_REL}" ]]; then
    echo "ERROR: Maven wrapper not found at ${service_dir}/${MVNW_REL}"
    return 1
  fi

  if ! "${MVNW_REL}" clean verify; then
    FAILED_PROJECTS+=("${project_key}: build/test failure")
    cd "${ROOT_DIR}"
    return 0
  fi

  local jacoco_path="${service_dir}/target/site/jacoco/jacoco.xml"
  if [[ ! -f "${jacoco_path}" ]]; then
    FAILED_PROJECTS+=("${project_key}: missing jacoco.xml")
    cd "${ROOT_DIR}"
    return 0
  fi

  local coverage
  coverage="$(python3 - <<PY
import xml.etree.ElementTree as ET
root = ET.parse("${jacoco_path}").getroot()
line = next((c for c in root.findall("counter") if c.attrib.get("type") == "LINE"), None)
if line is None:
    print("N/A")
else:
    covered = int(line.attrib["covered"])
    missed = int(line.attrib["missed"])
    total = covered + missed
    pct = (covered / total * 100) if total else 0
    print(f"{pct:.1f}%")
PY
)"
  SERVICE_COVERAGE+=("${project_key}=${coverage}")

  if "${MVNW_REL}" sonar:sonar \
      -Dsonar.projectKey="${project_key}" \
      -Dsonar.projectName="${project_name}" \
      -Dsonar.host.url="${SONAR_URL}" \
      -Dsonar.login="${SONAR_TOKEN}"; then
    SUCCESSFUL_PROJECTS+=("${project_key}")
    echo "OK: ${project_key} published"
  else
    FAILED_PROJECTS+=("${project_key}: sonar authorization or scan failure")
  fi

  cd "${ROOT_DIR}"
}

scan_frontend() {
  echo
  echo "====> Scanning frontend"
  cd "${ROOT_DIR}/frontend"

  if ! npm test -- --code-coverage --watch=false --browsers=ChromeHeadless; then
    FAILED_PROJECTS+=("skybooker-frontend: frontend test failure")
    cd "${ROOT_DIR}"
    return 0
  fi

  if [[ ! -f "${ROOT_DIR}/frontend/coverage/lcov.info" ]]; then
    FAILED_PROJECTS+=("skybooker-frontend: missing lcov.info")
    cd "${ROOT_DIR}"
    return 0
  fi

  local frontend_coverage
  frontend_coverage="$(python3 - <<'PY'
from pathlib import Path
lf = lh = 0
for line in Path("coverage/lcov.info").read_text().splitlines():
    if line.startswith("LF:"):
        lf += int(line[3:])
    elif line.startswith("LH:"):
        lh += int(line[3:])
pct = (lh / lf * 100) if lf else 0
print(f"{pct:.1f}%")
PY
)"
  SERVICE_COVERAGE+=("skybooker-frontend=${frontend_coverage}")

  if sonar-scanner \
    -Dsonar.projectKey=skybooker-frontend \
    -Dsonar.projectName="SkyBooker Frontend" \
    -Dsonar.sources=src \
    -Dsonar.host.url="${SONAR_URL}" \
    -Dsonar.login="${SONAR_TOKEN}" \
    -Dsonar.javascript.lcov.reportPaths=coverage/lcov.info; then
    SUCCESSFUL_PROJECTS+=("skybooker-frontend")
    echo "OK: skybooker-frontend published"
  else
    FAILED_PROJECTS+=("skybooker-frontend: sonar authorization or scan failure")
  fi

  cd "${ROOT_DIR}"
}

wait_for_sonarqube
cd "${ROOT_DIR}"

for service in "${BACKEND_SERVICES[@]}"; do
  scan_backend_service "${service}"
done

scan_frontend

echo
echo "All requested scans completed."
echo
echo "===== Coverage Snapshot (local reports) ====="
for entry in "${SERVICE_COVERAGE[@]}"; do
  echo "${entry}"
done
echo
echo "===== Sonar Publish Success ====="
if [[ ${#SUCCESSFUL_PROJECTS[@]} -eq 0 ]]; then
  echo "none"
else
  for p in "${SUCCESSFUL_PROJECTS[@]}"; do
    echo "${p}"
  done
fi
echo
echo "===== Sonar Publish Failures ====="
if [[ ${#FAILED_PROJECTS[@]} -eq 0 ]]; then
  echo "none"
else
  for p in "${FAILED_PROJECTS[@]}"; do
    echo "${p}"
  done
fi
