#!/usr/bin/env bash
# Valida a sessão temporária do AWS Academy antes de qualquer execução Terraform.
# Falha explicitamente quando as credenciais estão ausentes ou expiradas.
set -euo pipefail

missing=()
for name in AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY AWS_SESSION_TOKEN; do
  if [ -z "${!name:-}" ]; then
    missing+=("$name")
  fi
done

if [ ${#missing[@]} -gt 0 ]; then
  echo "Credenciais AWS ausentes no GitHub Environment: ${missing[*]}" >&2
  echo "Atualize os secrets com a sessão atual do Learner Lab e execute novamente." >&2
  exit 1
fi

if ! output=$(aws sts get-caller-identity --output json 2>&1); then
  echo "Falha ao validar a sessão AWS:" >&2
  echo "$output" >&2
  if printf '%s' "$output" | grep -qi 'ExpiredToken\|InvalidClientTokenId\|RequestExpired'; then
    echo "A sessão do AWS Academy expirou. Reinicie o laboratório e atualize os três secrets." >&2
  fi
  exit 1
fi

account=$(printf '%s' "$output" | python3 -c 'import json,sys; print(json.load(sys.stdin)["Account"])')
echo "Sessão AWS válida para a conta ${account} na região ${AWS_REGION:-us-west-2}."
