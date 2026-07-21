#!/usr/bin/env bash
# Roda o PITest (mutationCoverage) em app/backend-api e delega o parsing/
# relatorio a check_mutation.py. Nenhum arquivo proprio e criado por este
# script; o mutations.xml lido e artefato normal do PITest em target/.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODULE_DIR="$(cd "$SCRIPT_DIR/../../../../app/backend-api" && pwd)"

cd "$MODULE_DIR"
./mvnw -q org.pitest:pitest-maven:mutationCoverage

MUTATIONS_XML="$(find "$MODULE_DIR/target/pit-reports" -name mutations.xml -printf '%T@ %p\n' 2>/dev/null | sort -n | tail -1 | cut -d' ' -f2-)"
if [ -z "$MUTATIONS_XML" ] || [ ! -f "$MUTATIONS_XML" ]; then
  echo "STATUS: FAIL"
  echo "mutations.xml nao encontrado em $MODULE_DIR/target/pit-reports."
  echo "Verifique se o pitest-maven esta configurado no pom.xml (veja references/pom-plugins.md)."
  exit 1
fi

exec python3 "$SCRIPT_DIR/check_mutation.py" --mutations-xml "$MUTATIONS_XML" "$@"
