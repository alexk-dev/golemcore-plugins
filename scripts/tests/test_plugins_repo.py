import importlib.util
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock


def load_plugins_repo_module():
    module_path = Path(__file__).resolve().parents[1] / "plugins_repo.py"
    spec = importlib.util.spec_from_file_location("plugins_repo", module_path)
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


plugins_repo = load_plugins_repo_module()


class PluginsRepoReleaseTests(unittest.TestCase):
    def build_spec(self):
        module_dir = plugins_repo.ROOT / "golemcore" / "brave-search"
        return plugins_repo.PluginSpec(
            plugin_id="golemcore/brave-search",
            owner="golemcore",
            name="brave-search",
            version="1.2.0",
            plugin_api_version=1,
            engine_version=">=0.0.0 <1.0.0",
            entrypoint="me.golemcore.plugins.golemcore.bravesearch.BraveSearchPluginBootstrap",
            description="Brave Search tool plugin for GolemCore",
            source_url="https://github.com/alexk-dev/golemcore-plugins/tree/main/golemcore/brave-search",
            license_name="Apache-2.0",
            maintainers=["alexk-dev"],
            module_dir=module_dir,
            pom_path=module_dir / "pom.xml",
            plugin_yaml_path=module_dir / "plugin.yaml",
            artifact_id="golemcore-brave-search-plugin",
            registry_dir=plugins_repo.ROOT / "registry" / "golemcore" / "brave-search",
            bootstrap_path=module_dir / "src" / "main" / "java" / "Bootstrap.java",
        )

    def test_remote_package_version_exists_returns_false_for_missing_package(self):
        with mock.patch.object(plugins_repo, "github_packages_namespace", return_value="users"), mock.patch.object(
            plugins_repo,
            "run_command_result",
            return_value=subprocess.CompletedProcess(
                args=["gh", "api"],
                returncode=1,
                stdout="",
                stderr="HTTP 404: Not Found",
            ),
        ):
            exists = plugins_repo.remote_package_version_exists(
                "alexk-dev",
                "golemcore-brave-search-plugin",
                "1.3.0",
            )

        self.assertFalse(exists)

    def test_run_release_skips_existing_remote_package_before_mutating_repo(self):
        spec = self.build_spec()
        captured_outputs = {}

        with tempfile.NamedTemporaryFile() as github_output:
            with mock.patch.object(plugins_repo, "discover_plugins", return_value={spec.plugin_id: spec}), mock.patch.object(
                plugins_repo, "validate_repo"
            ), mock.patch.object(plugins_repo, "release_tag_exists", return_value=True), mock.patch.object(
                plugins_repo, "read_versions", return_value=["1.0.0", "1.2.0"]
            ), mock.patch.object(
                plugins_repo, "infer_auto_bump", return_value="minor"
            ), mock.patch.object(
                plugins_repo, "detect_repository_owner", return_value="alexk-dev"
            ), mock.patch.object(
                plugins_repo, "remote_package_version_exists", return_value=True
            ), mock.patch.object(
                plugins_repo,
                "write_github_output",
                side_effect=lambda path, values: captured_outputs.update(values),
            ), mock.patch.object(
                plugins_repo, "write_child_pom_version"
            ) as write_child_pom_version, mock.patch.object(
                plugins_repo, "write_text"
            ) as write_text, mock.patch.object(
                plugins_repo, "run_command"
            ) as run_command:
                plugins_repo.run_release(spec.plugin_id, "auto", None, Path(github_output.name))

        self.assertEqual(captured_outputs["plugin_id"], "golemcore/brave-search")
        self.assertEqual(captured_outputs["artifact_id"], "golemcore-brave-search-plugin")
        self.assertEqual(captured_outputs["version"], "1.3.0")
        self.assertEqual(captured_outputs["skipped_existing_package"], "true")
        write_child_pom_version.assert_not_called()
        write_text.assert_not_called()
        run_command.assert_not_called()


if __name__ == "__main__":
    unittest.main()
