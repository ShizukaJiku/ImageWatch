#!/usr/bin/env bash
# Instala los hooks de git de este repositorio.
set -euo pipefail

root="$(git rev-parse --show-toplevel)"

if [ ! -f "$root/.denylist.local" ]; then
  echo "Falta .denylist.local en la raíz del repositorio." >&2
  echo "Crea el fichero con un patrón de regex extendida por línea." >&2
  exit 1
fi

install -m 755 "$root/scripts/pre-commit" "$root/.git/hooks/pre-commit"
echo "Hook pre-commit instalado."
