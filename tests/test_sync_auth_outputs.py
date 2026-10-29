import importlib.util
import unittest
from pathlib import Path
from unittest.mock import call, patch

SCRIPT_PATH = Path(__file__).parents[1] / "scripts" / "sync-auth-outputs.py"
SPEC = importlib.util.spec_from_file_location("sync_auth_outputs", SCRIPT_PATH)
assert SPEC is not None and SPEC.loader is not None
sync_auth_outputs = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(sync_auth_outputs)


class RecordingGitHubClient:
    def __init__(self) -> None:
        self.variables: list[tuple[str, str, str, str]] = []

    def set_environment_variable(
        self,
        repository: str,
        environment: str,
        name: str,
        value: str,
    ) -> None:
        self.variables.append((repository, environment, name, value))


class SyncOutputsTest(unittest.TestCase):
    def test_syncs_api_gateway_urls_with_backend(self) -> None:
        client = RecordingGitHubClient()
        outputs = {
            "api_base_url": {
                "value": "https://abc.execute-api.us-west-2.amazonaws.com/"
            },
            "notification_endpoint": {
                "value": "https://abc.execute-api.us-west-2.amazonaws.com/internal/notifications"
            },
        }

        sync_auth_outputs.sync_outputs(
            client,
            outputs,
            "tiagomiele/oficina-backend-fiap-fase3",
            "homolog",
        )

        self.assertEqual(
            client.variables,
            [
                (
                    "tiagomiele/oficina-backend-fiap-fase3",
                    "homolog",
                    "API_GATEWAY_BASE_URL",
                    "https://abc.execute-api.us-west-2.amazonaws.com",
                ),
                (
                    "tiagomiele/oficina-backend-fiap-fase3",
                    "homolog",
                    "AUTH_BASE_URL",
                    "https://abc.execute-api.us-west-2.amazonaws.com",
                ),
                (
                    "tiagomiele/oficina-backend-fiap-fase3",
                    "homolog",
                    "NOTIFICATION_ENDPOINT",
                    "https://abc.execute-api.us-west-2.amazonaws.com/internal/notifications",
                ),
            ],
        )

    def test_rejects_missing_api_base_url(self) -> None:
        with self.assertRaisesRegex(
            sync_auth_outputs.SyncError,
            "Output obrigatório ausente: api_base_url",
        ):
            sync_auth_outputs.sync_outputs(
                RecordingGitHubClient(),
                {
                    "notification_endpoint": {
                        "value": "https://abc.execute-api.us-west-2.amazonaws.com/internal/notifications"
                    }
                },
                "tiagomiele/oficina-backend-fiap-fase3",
                "homolog",
            )

    def test_rejects_non_https_output(self) -> None:
        with self.assertRaisesRegex(
            sync_auth_outputs.SyncError,
            "Output obrigatório inválido: api_base_url",
        ):
            sync_auth_outputs.required_url(
                {"api_base_url": {"value": "http://example.com"}},
                "api_base_url",
            )

    def test_rejects_notification_endpoint_from_another_api(self) -> None:
        with self.assertRaisesRegex(
            sync_auth_outputs.SyncError,
            "notification_endpoint não pertence ao API Gateway esperado",
        ):
            sync_auth_outputs.sync_outputs(
                RecordingGitHubClient(),
                {
                    "api_base_url": {
                        "value": "https://abc.execute-api.us-west-2.amazonaws.com"
                    },
                    "notification_endpoint": {
                        "value": "https://other.example.com/internal/notifications"
                    },
                },
                "tiagomiele/oficina-backend-fiap-fase3",
                "homolog",
            )


class GitHubClientTest(unittest.TestCase):
    def setUp(self) -> None:
        self.client = sync_auth_outputs.GitHubClient("token")

    def test_creates_environment_variable_when_absent(self) -> None:
        with patch.object(
            self.client,
            "_request",
            side_effect=[None, {}],
        ) as request:
            self.client.set_environment_variable(
                "owner/repository", "homolog", "AUTH_BASE_URL", "https://example.com"
            )

        self.assertEqual(
            request.call_args_list,
            [
                call(
                    "GET",
                    "/repos/owner/repository/environments/homolog/variables/AUTH_BASE_URL",
                    allow_not_found=True,
                ),
                call(
                    "POST",
                    "/repos/owner/repository/environments/homolog/variables",
                    {"name": "AUTH_BASE_URL", "value": "https://example.com"},
                ),
            ],
        )

    def test_updates_environment_variable_when_present(self) -> None:
        with patch.object(
            self.client,
            "_request",
            side_effect=[{"name": "AUTH_BASE_URL"}, {}],
        ) as request:
            self.client.set_environment_variable(
                "owner/repository",
                "production",
                "AUTH_BASE_URL",
                "https://example.com",
            )

        self.assertEqual(
            request.call_args_list,
            [
                call(
                    "GET",
                    "/repos/owner/repository/environments/production/variables/AUTH_BASE_URL",
                    allow_not_found=True,
                ),
                call(
                    "PATCH",
                    "/repos/owner/repository/environments/production/variables/AUTH_BASE_URL",
                    {"name": "AUTH_BASE_URL", "value": "https://example.com"},
                ),
            ],
        )


if __name__ == "__main__":
    unittest.main()
