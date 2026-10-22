#!/usr/bin/env python3

import argparse
import json
import os
import sys
from pathlib import Path
from typing import Mapping
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urlparse
from urllib.request import Request, urlopen

GITHUB_API_BASE_URL = "https://api.github.com"


class SyncError(RuntimeError):
    pass


class GitHubClient:
    def __init__(self, token: str) -> None:
        if not token.strip():
            raise SyncError("GITHUB_SYNC_TOKEN não configurado.")
        self._token = token

    def _request(
        self,
        method: str,
        path: str,
        payload: Mapping[str, object] | None = None,
        *,
        allow_not_found: bool = False,
    ) -> Mapping[str, object] | None:
        data = json.dumps(payload).encode("utf-8") if payload is not None else None
        request = Request(
            f"{GITHUB_API_BASE_URL}{path}",
            data=data,
            method=method,
            headers={
                "Accept": "application/vnd.github+json",
                "Authorization": f"Bearer {self._token}",
                "Content-Type": "application/json",
                "X-GitHub-Api-Version": "2022-11-28",
            },
        )
        try:
            with urlopen(request, timeout=30) as response:
                body = response.read()
        except HTTPError as error:
            if allow_not_found and error.code == 404:
                return None
            detail = error.read().decode("utf-8", errors="replace")
            raise SyncError(
                f"GitHub API retornou HTTP {error.code} em {method} {path}: {detail}"
            ) from error
        except URLError as error:
            raise SyncError(f"Falha ao acessar GitHub API: {error.reason}") from error

        if not body:
            return {}
        document = json.loads(body)
        if not isinstance(document, dict):
            raise SyncError(f"Resposta inesperada da GitHub API em {method} {path}.")
        return document

    def set_environment_variable(
        self,
        repository: str,
        environment: str,
        name: str,
        value: str,
    ) -> None:
        repository_path = quote(repository, safe="/")
        environment_path = quote(environment, safe="")
        name_path = quote(name, safe="")
        collection_path = (
            f"/repos/{repository_path}/environments/{environment_path}/variables"
        )
        variable_path = f"{collection_path}/{name_path}"
        existing = self._request("GET", variable_path, allow_not_found=True)
        if existing is None:
            self._request(
                "POST",
                collection_path,
                {"name": name, "value": value},
            )
            return
        self._request("PATCH", variable_path, {"name": name, "value": value})


def required_url(outputs: Mapping[str, object], name: str) -> str:
    output = outputs.get(name)
    if not isinstance(output, dict):
        raise SyncError(f"Output obrigatório ausente: {name}.")
    value = output.get("value")
    if not isinstance(value, str):
        raise SyncError(f"Output obrigatório inválido: {name}.")
    parsed = urlparse(value)
    if parsed.scheme != "https" or not parsed.netloc:
        raise SyncError(f"Output obrigatório inválido: {name}.")
    return value.rstrip("/")


def load_outputs(path: Path) -> Mapping[str, object]:
    try:
        document = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as error:
        raise SyncError(f"Arquivo de outputs não encontrado: {path}") from error
    except json.JSONDecodeError as error:
        raise SyncError(f"Arquivo de outputs inválido: {path}") from error
    if not isinstance(document, dict):
        raise SyncError("O documento de outputs deve ser um objeto JSON.")
    return document


def sync_outputs(
    github_client: GitHubClient,
    outputs: Mapping[str, object],
    backend_repository: str,
    environment: str,
) -> None:
    api_base_url = required_url(outputs, "api_base_url")
    notification_endpoint = required_url(outputs, "notification_endpoint")
    if not notification_endpoint.startswith(f"{api_base_url}/"):
        raise SyncError("notification_endpoint não pertence ao API Gateway esperado.")

    github_client.set_environment_variable(
        backend_repository, environment, "API_GATEWAY_BASE_URL", api_base_url
    )
    github_client.set_environment_variable(
        backend_repository, environment, "AUTH_BASE_URL", api_base_url
    )
    github_client.set_environment_variable(
        backend_repository,
        environment,
        "NOTIFICATION_ENDPOINT",
        notification_endpoint,
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Sincroniza outputs do Auth com o GitHub Environment do Backend."
    )
    parser.add_argument("--outputs-file", type=Path, required=True)
    parser.add_argument(
        "--backend-repository",
        default="tiagomiele/oficina-os-service",
    )
    parser.add_argument(
        "--environment",
        choices=("homolog", "production"),
        required=True,
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    try:
        token = os.environ.get("GITHUB_SYNC_TOKEN", "")
        outputs = load_outputs(args.outputs_file)
        sync_outputs(
            GitHubClient(token),
            outputs,
            args.backend_repository,
            args.environment,
        )
    except SyncError as error:
        print(f"Erro: {error}", file=sys.stderr)
        return 1

    print(
        "Outputs do Auth sincronizados com "
        f"{args.backend_repository} ({args.environment})."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
