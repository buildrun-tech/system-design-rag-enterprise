#!/usr/bin/env bash
# Roda mvn test + jacoco:report em app/backend-api e delega o parsing/relatorio
# a check_coverage.py. Nenhum arquivo proprio e criado por este script; o
# jacoco.xml lido e artefato normal do build Maven em target/.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODULE_DIR="$(cd "$SCRIPT_DIR/../../../../app/backend-api" && pwd)"

cd "$MODULE_DIR"
./mvnw -q test jacoco:report

JACOCO_XML="$MODULE_DIR/target/site/jacoco/jacoco.xml"
if [ ! -f "$JACOCO_XML" ]; then
  echo "STATUS: FAIL"
  echo "jacoco.xml nao encontrado em $JACOCO_XML."
  echo "Verifique se o jacoco-maven-plugin esta configurado no pom.xml (veja references/pom-plugins.md)."
  exit 1
fi

exec python3 "$SCRIPT_DIR/check_coverage.py" --jacoco-xml "$JACOCO_XML" "$@"
