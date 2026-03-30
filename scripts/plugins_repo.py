#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as et
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
POM_NS = "http://maven.apache.org/POM/4.0.0"
POM = {"m": POM_NS}
et.register_namespace("", POM_NS)

SEMVER_RE = re.compile(r"^(?P<major>0|[1-9]\d*)\.(?P<minor>0|[1-9]\d*)\.(?P<patch>0|[1-9]\d*)(?:-(?P<pre>[0-9A-Za-z.-]+))?$")
CONVENTIONAL_RE = re.compile(
    r"^(?P<type>[a-z]+)(?:\((?P<scope>[^)]+)\))?(?P<breaking>!)?: (?P<subject>.+)$"
)
SUPPORTED_COMMIT_TYPES = {"feat", "fix", "perf", "refactor", "build", "ci", "docs", "test", "chore", "revert", "release"}
PATCH_BUMP_TYPES = {"fix", "perf", "refactor", "build", "revert"}
NON_BUMP_TYPES = {"ci", "docs", "test", "chore", "release"}
COMMON_RELEASE_PATHS = ("pom.xml", "extension-api", "runtime-api", "misc")


@dataclass(frozen=True)
class PluginSpec:
    plugin_id: str
    owner: str
    name: str
    version: str
    plugin_api_version: int
    engine_version: str
    entrypoint: str
    description: str
    source_url: str
    license_name: str
    maintainers: list[str]
    module_dir: Path
    pom_path: Path
    plugin_yaml_path: Path
    artifact_id: str
    registry_dir: Path
    bootstrap_path: Path

    @property
    def module_path(self) -> str:
        return self.module_dir.relative_to(ROOT).as_posix()

    @property
    def registry_index_path(self) -> Path:
        return self.registry_dir / "index.yaml"

    @property
    def versions_dir(self) -> Path:
        return self.registry_dir / "versions"

    @property
    def current_registry_version_path(self) -> Path:
        return self.versions_dir / f"{self.version}.yaml"

    def dist_artifact_relative_path(self, version: str) -> Path:
        return Path("dist") / self.owner / self.name / version / f"{self.artifact_id}-{version}.jar"

    def release_tag(self, version: str) -> str:
        return f"{self.owner}-{self.name}-v{version}"


@dataclass(frozen=True)
class RepoSettings:
    java_version: str


@dataclass(frozen=True)
class ConventionalCommit:
    sha: str
    subject: str
    body: str
    type: str
    breaking: bool


def run_command(*args: str, capture_output: bool = True) -> str:
    result = subprocess.run(
        args,
        cwd=ROOT,
        check=True,
        text=True,
        capture_output=capture_output,
    )
    return result.stdout.strip() if capture_output else ""


def run_command_result(*args: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        args,
        cwd=ROOT,
        text=True,
        capture_output=True,
        check=False,
    )


def detect_repository_owner() -> str | None:
    owner = os.environ.get("GITHUB_REPOSITORY_OWNER")
    if owner and owner.strip():
        return owner.strip()

    repository = os.environ.get("GITHUB_REPOSITORY")
    if repository and "/" in repository:
        return repository.split("/", 1)[0].strip() or None
    return None


def github_packages_namespace(owner: str) -> str:
    result = run_command_result("gh", "api", f"/users/{owner}")
    if result.returncode != 0:
        error = result.stderr.strip() or result.stdout.strip()
        raise SystemExit(f"Unable to resolve GitHub owner type for {owner}: {error}")

    try:
        payload = json.loads(result.stdout)
    except json.JSONDecodeError as error:
        raise SystemExit(f"Unable to parse GitHub owner payload for {owner}: {error}") from error

    return "orgs" if str(payload.get("type")) == "Organization" else "users"


def remote_package_version_exists(owner: str, artifact_id: str, version: str) -> bool:
    package_name = f"me.golemcore.plugins.{artifact_id}"
    namespace = github_packages_namespace(owner)
    result = run_command_result("gh", "api", f"/{namespace}/{owner}/packages/maven/{package_name}/versions?per_page=100")
    if result.returncode != 0:
        error = result.stderr.strip() or result.stdout.strip()
        if "404" in error:
            return False
        raise SystemExit(f"Unable to read GitHub Packages versions for {package_name}: {error}")

    try:
        payload = json.loads(result.stdout)
    except json.JSONDecodeError as error:
        raise SystemExit(f"Unable to parse GitHub Packages payload for {package_name}: {error}") from error

    if not isinstance(payload, list):
        raise SystemExit(f"Unexpected GitHub Packages response for {package_name}: expected a list of versions")

    return any(str(item.get("name")) == version for item in payload if isinstance(item, dict))


def parse_scalar(raw: str):
    value = raw.strip()
    if value.startswith('"') and value.endswith('"'):
        return value[1:-1]
    if value.startswith("'") and value.endswith("'"):
        return value[1:-1]
    if value.isdigit():
        return int(value)
    return value


def parse_simple_yaml(path: Path) -> dict[str, object]:
    data: dict[str, object] = {}
    lines = path.read_text(encoding="utf-8").splitlines()
    index = 0
    while index < len(lines):
        line = lines[index]
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            index += 1
            continue
        if line.startswith("  - "):
            raise ValueError(f"Unexpected list item without key in {path}: {line}")
        if ":" not in line:
            raise ValueError(f"Unsupported YAML line in {path}: {line}")
        key, raw_value = line.split(":", 1)
        key = key.strip()
        raw_value = raw_value.rstrip()
        if raw_value.strip():
            data[key] = parse_scalar(raw_value)
            index += 1
            continue
        index += 1
        items: list[str] = []
        while index < len(lines) and lines[index].startswith("  - "):
            items.append(parse_scalar(lines[index][4:]))  # type: ignore[arg-type]
            index += 1
        data[key] = items
    return data


def yaml_quote(value: str) -> str:
    escaped = value.replace("\\", "\\\\").replace('"', '\\"')
    return f'"{escaped}"'


def semver_key(version: str):
    match = SEMVER_RE.fullmatch(version)
    if not match:
        raise ValueError(f"Invalid SemVer: {version}")
    major = int(match.group("major"))
    minor = int(match.group("minor"))
    patch = int(match.group("patch"))
    pre = match.group("pre")
    if pre is None:
        pre_key = (1, ())
    else:
        pre_parts: list[tuple[int, object]] = []
        for part in pre.split("."):
            if part.isdigit():
                pre_parts.append((0, int(part)))
            else:
                pre_parts.append((1, part))
        pre_key = (0, tuple(pre_parts))
    return major, minor, patch, pre_key


def compare_versions(left: str, right: str) -> int:
    left_key = semver_key(left)
    right_key = semver_key(right)
    if left_key < right_key:
        return -1
    if left_key > right_key:
        return 1
    return 0


def bump_version(version: str, bump: str) -> str:
    match = SEMVER_RE.fullmatch(version)
    if not match:
        raise ValueError(f"Invalid SemVer: {version}")
    major = int(match.group("major"))
    minor = int(match.group("minor"))
    patch = int(match.group("patch"))
    if bump == "major":
        return f"{major + 1}.0.0"
    if bump == "minor":
        return f"{major}.{minor + 1}.0"
    if bump == "patch":
        return f"{major}.{minor}.{patch + 1}"
    raise ValueError(f"Unsupported bump type: {bump}")


def read_required_text(parent: et.Element | None, xpath: str, source: Path) -> str:
    if parent is None:
        raise ValueError(f"Missing XML node {xpath} in {source}")
    element = parent.find(xpath, POM)
    if element is None or element.text is None or not element.text.strip():
        raise ValueError(f"Missing XML node {xpath} in {source}")
    return element.text.strip()


def load_repo_settings() -> RepoSettings:
    pom_path = ROOT / "pom.xml"
    root = et.parse(pom_path).getroot()
    properties = root.find("m:properties", POM)
    java_version = read_required_text(properties, "m:java.version", pom_path)
    return RepoSettings(java_version=java_version)


def read_dependencies(path: Path) -> list[tuple[str, str, str | None]]:
    root = et.parse(path).getroot()
    dependencies: list[tuple[str, str, str | None]] = []
    for dependency in root.findall("m:dependencies/m:dependency", POM):
        group_id = read_required_text(dependency, "m:groupId", path)
        artifact_id = read_required_text(dependency, "m:artifactId", path)
        scope_element = dependency.find("m:scope", POM)
        scope = scope_element.text.strip() if scope_element is not None and scope_element.text else None
        dependencies.append((group_id, artifact_id, scope))
    return dependencies


def read_dependency_management_versions(path: Path) -> dict[tuple[str, str], str]:
    root = et.parse(path).getroot()
    versions: dict[tuple[str, str], str] = {}
    for dependency in root.findall("m:dependencyManagement/m:dependencies/m:dependency", POM):
        group_id = read_required_text(dependency, "m:groupId", path)
        artifact_id = read_required_text(dependency, "m:artifactId", path)
        version = read_required_text(dependency, "m:version", path)
        versions[(group_id, artifact_id)] = version
    return versions


def read_pom_version(path: Path) -> str:
    root = et.parse(path).getroot()
    version = root.find("m:version", POM)
    if version is None or version.text is None or not version.text.strip():
        raise ValueError(f"Missing version in {path}")
    return version.text.strip()


def read_pom_property(properties: et.Element | None, name: str, source: Path) -> str:
    if properties is None:
        raise ValueError(f"{source}: missing properties section")
    element = properties.find(f"{{{POM_NS}}}{name}")
    if element is None or element.text is None or not element.text.strip():
        raise ValueError(f"{source}: {name} must be defined")
    return element.text.strip()


def read_build_plugin_artifact_ids(path: Path) -> set[str]:
    root = et.parse(path).getroot()
    plugins = root.findall("m:build/m:plugins/m:plugin", POM)
    artifact_ids: set[str] = set()
    for plugin in plugins:
        artifact = plugin.find("m:artifactId", POM)
        if artifact is not None and artifact.text and artifact.text.strip():
            artifact_ids.add(artifact.text.strip())
    return artifact_ids


def read_child_pom(path: Path) -> tuple[str, str]:
    root = et.parse(path).getroot()
    artifact = root.find("m:artifactId", POM)
    version = root.find("m:version", POM)
    if artifact is None or artifact.text is None:
        raise ValueError(f"Missing artifactId in {path}")
    if version is None or version.text is None:
        raise ValueError(f"Child module must declare its own version in {path}")
    return artifact.text.strip(), version.text.strip()


def write_child_pom_version(path: Path, version: str) -> None:
    tree = et.parse(path)
    root = tree.getroot()
    version_element = root.find("m:version", POM)
    if version_element is None:
        raise ValueError(f"Child module must declare its own version in {path}")
    version_element.text = version
    tree.write(path, encoding="utf-8", xml_declaration=True)


def entrypoint_to_path(entrypoint: str) -> Path:
    return Path(*entrypoint.split(".")).with_suffix(".java")


def discover_plugins() -> dict[str, PluginSpec]:
    plugins: dict[str, PluginSpec] = {}
    for plugin_yaml in sorted(ROOT.glob("*/*/plugin.yaml")):
        module_dir = plugin_yaml.parent
        owner = module_dir.parent.name
        name = module_dir.name
        pom_path = module_dir / "pom.xml"
        if not pom_path.exists():
            continue
        manifest = parse_simple_yaml(plugin_yaml)
        artifact_id, version = read_child_pom(pom_path)
        plugin_id = str(manifest["id"])
        spec = PluginSpec(
            plugin_id=plugin_id,
            owner=owner,
            name=name,
            version=version,
            plugin_api_version=int(manifest["pluginApiVersion"]),
            engine_version=str(manifest["engineVersion"]),
            entrypoint=str(manifest["entrypoint"]),
            description=str(manifest["description"]),
            source_url=str(manifest["sourceUrl"]),
            license_name=str(manifest["license"]),
            maintainers=[str(item) for item in manifest.get("maintainers", [])],
            module_dir=module_dir,
            pom_path=pom_path,
            plugin_yaml_path=plugin_yaml,
            artifact_id=artifact_id,
            registry_dir=ROOT / "registry" / owner / name,
            bootstrap_path=module_dir / "src" / "main" / "java" / entrypoint_to_path(str(manifest["entrypoint"])),
        )
        plugins[spec.plugin_id] = spec
    return plugins


def parse_conventional_commit(sha: str, subject: str, body: str) -> ConventionalCommit:
    match = CONVENTIONAL_RE.match(subject.strip())
    if not match:
        raise ValueError(f"Commit {sha} subject '{subject.strip()}' is not a conventional commit")
    commit_type = match.group("type")
    if commit_type not in SUPPORTED_COMMIT_TYPES:
        supported = ", ".join(sorted(SUPPORTED_COMMIT_TYPES))
        raise ValueError(
            f"Commit {sha} subject '{subject.strip()}' uses unsupported type '{commit_type}'. Supported types: {supported}"
        )
    return ConventionalCommit(
        sha=sha,
        subject=subject.strip(),
        body=body,
        type=commit_type,
        breaking=bool(match.group("breaking")) or "BREAKING CHANGE:" in body,
    )


def read_commit_range(range_spec: str, *paths: str) -> list[ConventionalCommit]:
    command = ["git", "log", "--format=%H%x1f%s%x1f%b%x1e", range_spec]
    if paths:
        command.extend(["--", *paths])
    log_output = run_command(*command)
    commits: list[ConventionalCommit] = []
    for record in [entry for entry in log_output.split("\x1e") if entry.strip()]:
        sha, subject, body = (record.split("\x1f", 2) + ["", ""])[:3]
        commits.append(parse_conventional_commit(sha.strip(), subject, body))
    return commits


def read_versions(index_path: Path) -> list[str]:
    if not index_path.exists():
        return []
    data = parse_simple_yaml(index_path)
    versions = data.get("versions", [])
    if not isinstance(versions, list):
        raise ValueError(f"Invalid versions list in {index_path}")
    return [str(version) for version in versions]


def repo_http_url() -> str:
    remote_url = run_command("git", "remote", "get-url", "origin")
    normalized = remote_url.removesuffix(".git")
    if normalized.startswith("git@github.com:"):
        return "https://github.com/" + normalized.removeprefix("git@github.com:")
    if normalized.startswith("https://github.com/"):
        return normalized
    raise ValueError(f"Unsupported origin URL for GitHub Releases: {remote_url}")


def render_plugin_yaml(spec: PluginSpec, version: str) -> str:
    lines = [
        f"id: {spec.plugin_id}",
        f"provider: {spec.owner}",
        f"name: {spec.name}",
        f"version: {version}",
        f"pluginApiVersion: {spec.plugin_api_version}",
        f"engineVersion: {yaml_quote(spec.engine_version)}",
        f"entrypoint: {spec.entrypoint}",
        f"description: {yaml_quote(spec.description)}",
        f"sourceUrl: {yaml_quote(spec.source_url)}",
        f"license: {yaml_quote(spec.license_name)}",
        "maintainers:",
    ]
    for maintainer in spec.maintainers:
        lines.append(f"  - {maintainer}")
    return "\n".join(lines) + "\n"


def render_registry_index(spec: PluginSpec, versions: list[str]) -> str:
    latest = sorted(versions, key=semver_key)[-1]
    lines = [
        f"id: {spec.plugin_id}",
        f"owner: {spec.owner}",
        f"name: {spec.name}",
        f"latest: {latest}",
        "versions:",
    ]
    for version in sorted(versions, key=semver_key):
        lines.append(f"  - {version}")
    lines.append(f"source: {yaml_quote(spec.source_url)}")
    return "\n".join(lines) + "\n"


def render_registry_version(
    spec: PluginSpec,
    version: str,
    published_at: str,
    source_commit: str,
    artifact_url: str | None = None,
) -> str:
    resolved_artifact_url = artifact_url or spec.dist_artifact_relative_path(version).as_posix()
    lines = [
        f"id: {spec.plugin_id}",
        f"version: {version}",
        f"pluginApiVersion: {spec.plugin_api_version}",
        f"engineVersion: {yaml_quote(spec.engine_version)}",
        f"artifactUrl: {yaml_quote(resolved_artifact_url)}",
        f"publishedAt: {yaml_quote(published_at)}",
        f"sourceCommit: {yaml_quote(source_commit)}",
        f"entrypoint: {spec.entrypoint}",
        f"sourceUrl: {yaml_quote(spec.source_url)}",
        f"license: {yaml_quote(spec.license_name)}",
        "maintainers:",
    ]
    for maintainer in spec.maintainers:
        lines.append(f"  - {maintainer}")
    return "\n".join(lines) + "\n"


def write_text(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def release_tag_exists(spec: PluginSpec, version: str) -> bool:
    tag = spec.release_tag(version)
    result = subprocess.run(
        ["git", "rev-parse", "-q", "--verify", f"refs/tags/{tag}"],
        cwd=ROOT,
        text=True,
        capture_output=True,
        check=False,
    )
    return result.returncode == 0


def latest_release_tag(spec: PluginSpec) -> tuple[str | None, str | None]:
    tags = run_command("git", "tag", "--list", f"{spec.owner}-{spec.name}-v*").splitlines()
    latest_tag = None
    latest_version = None
    for tag in tags:
        version = tag.removeprefix(f"{spec.owner}-{spec.name}-v")
        if not SEMVER_RE.fullmatch(version):
            continue
        if latest_version is None or compare_versions(version, latest_version) > 0:
            latest_version = version
            latest_tag = tag
    return latest_tag, latest_version


def read_changed_paths(range_spec: str) -> list[str]:
    diff_output = run_command("git", "diff", "--name-only", range_spec)
    return [line.strip() for line in diff_output.splitlines() if line.strip()]


def release_commit_paths(spec: PluginSpec) -> list[str]:
    return [
        spec.module_path,
        spec.registry_dir.relative_to(ROOT).as_posix(),
        *COMMON_RELEASE_PATHS,
    ]


def infer_auto_bump_candidate(spec: PluginSpec) -> str | None:
    latest_tag, _ = latest_release_tag(spec)
    if latest_tag is None:
        return "initial"

    range_spec = f"{latest_tag}..HEAD"
    commits = read_commit_range(range_spec, *release_commit_paths(spec))
    if not commits:
        return None

    found_minor = False
    found_patch = False
    for commit in commits:
        if commit.breaking:
            return "major"
        if commit.type == "feat":
            found_minor = True
        elif commit.type in PATCH_BUMP_TYPES:
            found_patch = True

    if found_minor:
        return "minor"
    if found_patch:
        return "patch"
    return None


def collect_release_candidates(range_spec: str) -> list[PluginSpec]:
    plugins = discover_plugins()
    if not plugins:
        return []

    changed_paths = read_changed_paths(range_spec)
    has_common_release_change = any(
        path == common_path or path.startswith(f"{common_path}/") for path in changed_paths for common_path in COMMON_RELEASE_PATHS
    )

    candidates: list[PluginSpec] = []
    for spec in plugins.values():
        if not release_tag_exists(spec, spec.version):
            candidates.append(spec)
            continue

        plugin_changed = any(path == spec.module_path or path.startswith(f"{spec.module_path}/") for path in changed_paths)
        if not has_common_release_change and not plugin_changed:
            continue

        if infer_auto_bump_candidate(spec) is not None:
            candidates.append(spec)

    return sorted(candidates, key=lambda item: item.plugin_id)


def emit_release_plan(rev_range: str) -> None:
    for spec in collect_release_candidates(rev_range):
        print(spec.plugin_id)


def validate_repo() -> None:
    plugins = discover_plugins()
    if not plugins:
        raise SystemExit("No plugins discovered")

    root_pom_path = ROOT / "pom.xml"
    root_pom = et.parse(root_pom_path).getroot()
    root_modules = [module.text.strip() for module in root_pom.findall("m:modules/m:module", POM) if module.text]
    errors: list[str] = []
    try:
        repo_settings = load_repo_settings()
    except ValueError as error:
        errors.append(str(error))
    else:
        if repo_settings.java_version != "25":
            errors.append(f"{ROOT / 'pom.xml'}: java.version must be 25, got {repo_settings.java_version}")
        if "extension-api" not in root_modules:
            errors.append(f"{ROOT / 'pom.xml'}: missing module extension-api")
        if "runtime-api" not in root_modules:
            errors.append(f"{ROOT / 'pom.xml'}: missing module runtime-api")

    root_properties = root_pom.find("m:properties", POM)
    try:
        plugin_api_version = read_pom_property(root_properties, "plugin.api.version", root_pom_path)
    except ValueError as error:
        errors.append(str(error))
    else:
        if not SEMVER_RE.fullmatch(plugin_api_version):
            errors.append(f"{root_pom_path}: plugin.api.version must be a valid SemVer, got {plugin_api_version}")

    dependency_management_versions = read_dependency_management_versions(root_pom_path)
    expected_api_version_ref = "${plugin.api.version}"
    for artifact_id in ("golemcore-plugin-extension-api", "golemcore-plugin-runtime-api"):
        coordinate = ("me.golemcore.plugins", artifact_id)
        actual_version = dependency_management_versions.get(coordinate)
        if actual_version != expected_api_version_ref:
            errors.append(
                f"{root_pom_path}: dependencyManagement for {coordinate[0]}:{artifact_id} must use "
                f"{expected_api_version_ref}, got {actual_version or '<missing>'}"
            )

    for module_path in ("extension-api", "runtime-api"):
        module_pom = ROOT / module_path / "pom.xml"
        if not module_pom.exists():
            continue
        try:
            module_version = read_pom_version(module_pom)
        except ValueError as error:
            errors.append(str(error))
            continue
        if plugin_api_version is not None and module_version != plugin_api_version:
            errors.append(f"{module_pom}: version must match plugin.api.version {plugin_api_version}, got {module_version}")

    formatter_required_modules = {"extension-api", "runtime-api"}
    formatter_plugin_artifact = "formatter-maven-plugin"
    for module_path in formatter_required_modules:
        if module_path not in root_modules:
            continue
        module_pom = ROOT / module_path / "pom.xml"
        if formatter_plugin_artifact not in read_build_plugin_artifact_ids(module_pom):
            errors.append(f"{module_pom}: {formatter_plugin_artifact} must be enabled for internal API modules")

    for spec in plugins.values():
        expected_id = f"{spec.owner}/{spec.name}"
        if spec.plugin_id != expected_id:
            errors.append(f"{spec.plugin_yaml_path}: id must be {expected_id}, got {spec.plugin_id}")
        if spec.version != str(parse_simple_yaml(spec.plugin_yaml_path)["version"]):
            errors.append(f"{spec.plugin_yaml_path}: version must match {spec.pom_path}")
        if spec.module_path not in root_modules:
            errors.append(f"{ROOT / 'pom.xml'}: missing module {spec.module_path}")
        if not spec.artifact_id.endswith("-plugin"):
            errors.append(f"{spec.pom_path}: artifactId must end with -plugin")
        dependencies = read_dependencies(spec.pom_path)
        build_plugins = read_build_plugin_artifact_ids(spec.pom_path)
        if ("me.golemcore", "bot", "provided") in dependencies or ("me.golemcore", "bot", None) in dependencies:
            errors.append(f"{spec.pom_path}: plugin modules must not depend on me.golemcore:bot directly")
        if ("me.golemcore.plugins", "golemcore-plugin-extension-api", "provided") not in dependencies:
            errors.append(
                f"{spec.pom_path}: plugin modules must depend on me.golemcore.plugins:golemcore-plugin-extension-api with provided scope"
            )
        if spec.owner == "golemcore" and formatter_plugin_artifact not in build_plugins:
            errors.append(f"{spec.pom_path}: {formatter_plugin_artifact} must be enabled for golemcore-owned plugins")
        if not spec.bootstrap_path.exists():
            errors.append(f"Missing bootstrap source file: {spec.bootstrap_path}")
        else:
            bootstrap_source = spec.bootstrap_path.read_text(encoding="utf-8")
            if ".version(" in bootstrap_source:
                errors.append(f"{spec.bootstrap_path}: bootstrap must not hard-code version")
            if ".pluginApiVersion(" in bootstrap_source:
                errors.append(f"{spec.bootstrap_path}: bootstrap must not hard-code pluginApiVersion")
            if ".engineVersion(" in bootstrap_source:
                errors.append(f"{spec.bootstrap_path}: bootstrap must not hard-code engineVersion")

        if not SEMVER_RE.fullmatch(spec.version):
            errors.append(f"{spec.plugin_yaml_path}: invalid SemVer {spec.version}")

        if not spec.registry_index_path.exists():
            errors.append(f"Missing registry index: {spec.registry_index_path}")
            continue

        index_data = parse_simple_yaml(spec.registry_index_path)
        versions = read_versions(spec.registry_index_path)
        if not versions:
            errors.append(f"{spec.registry_index_path}: versions list must not be empty")
        else:
            latest = str(index_data["latest"])
            calculated_latest = sorted(versions, key=semver_key)[-1]
            if latest != calculated_latest:
                errors.append(
                    f"{spec.registry_index_path}: latest={latest} does not match highest version {calculated_latest}"
                )
            if spec.version != latest:
                errors.append(
                    f"{spec.registry_index_path}: current module version {spec.version} must match latest {latest}"
                )

        current_version_path = spec.current_registry_version_path
        if not current_version_path.exists():
            errors.append(f"Missing registry version file for current version: {current_version_path}")
        else:
            version_data = parse_simple_yaml(current_version_path)
            if str(version_data["id"]) != spec.plugin_id:
                errors.append(f"{current_version_path}: id must match {spec.plugin_id}")
            if str(version_data["version"]) != spec.version:
                errors.append(f"{current_version_path}: version must match current module version {spec.version}")
            artifact_url = str(version_data["artifactUrl"])
            local_artifact = spec.dist_artifact_relative_path(spec.version).as_posix()
            if artifact_url != local_artifact:
                errors.append(
                    f"{current_version_path}: artifactUrl must be {local_artifact} for the current local marketplace build"
                )
            if int(version_data["pluginApiVersion"]) != spec.plugin_api_version:
                errors.append(f"{current_version_path}: pluginApiVersion must match plugin.yaml")
            if str(version_data["engineVersion"]) != spec.engine_version:
                errors.append(f"{current_version_path}: engineVersion must match plugin.yaml")
            if str(version_data["entrypoint"]) != spec.entrypoint:
                errors.append(f"{current_version_path}: entrypoint must match plugin.yaml")

        for version in versions:
            version_file = spec.versions_dir / f"{version}.yaml"
            if not version_file.exists():
                errors.append(f"{spec.registry_index_path}: missing version file {version_file}")

    if errors:
        raise SystemExit("\n".join(errors))


def infer_auto_bump(spec: PluginSpec) -> str:
    latest_tag, _ = latest_release_tag(spec)
    if latest_tag is None:
        raise SystemExit(
            f"Cannot infer release bump for {spec.plugin_id}: no prior release tag found. Use --version-override or an explicit bump."
        )

    range_spec = f"{latest_tag}..HEAD"
    commits = read_commit_range(range_spec, *release_commit_paths(spec))
    if not commits:
        raise SystemExit(f"No changes detected for {spec.plugin_id} since {latest_tag}")

    found_minor = False
    found_patch = False
    non_bump_types: set[str] = set()
    for commit in commits:
        if commit.breaking:
            return "major"
        if commit.type == "feat":
            found_minor = True
        elif commit.type in PATCH_BUMP_TYPES:
            found_patch = True
        elif commit.type in NON_BUMP_TYPES:
            non_bump_types.add(commit.type)

    if found_minor:
        return "minor"
    if found_patch:
        return "patch"
    if non_bump_types:
        listed = ", ".join(sorted(non_bump_types))
        raise SystemExit(
            f"Only non-release commit types detected for {spec.plugin_id} since {latest_tag}: {listed}. "
            "Use an explicit bump or add a release-impacting change."
        )
    raise SystemExit(f"Unable to infer SemVer bump for {spec.plugin_id} from commits since {latest_tag}")


def write_github_output(path: Path | None, values: dict[str, str]) -> None:
    if path is None:
        return
    with path.open("a", encoding="utf-8") as handle:
        for key, value in values.items():
            handle.write(f"{key}={value}\n")


def lint_commits(rev_range: str) -> None:
    commits = read_commit_range(rev_range)
    if not commits:
        print(f"No commits found in {rev_range}")
        return
    print(f"Validated {len(commits)} conventional commits in {rev_range}")


def emit_repo_settings(github_output: Path | None) -> None:
    settings = load_repo_settings()
    values = {
        "java_version": settings.java_version,
    }
    write_github_output(github_output, values)
    for key, value in values.items():
        print(f"{key}={value}")


def run_release(plugin_id: str, bump: str, version_override: str | None, github_output: Path | None) -> None:
    plugins = discover_plugins()
    spec = plugins.get(plugin_id)
    if spec is None:
        raise SystemExit(f"Unknown plugin: {plugin_id}")

    validate_repo()

    current_tag_exists = release_tag_exists(spec, spec.version)
    current_versions = read_versions(spec.registry_index_path)
    if version_override:
        new_version = version_override
        if not SEMVER_RE.fullmatch(new_version):
            raise SystemExit(f"Invalid version override: {new_version}")
    elif bump == "auto" and not current_tag_exists:
        new_version = spec.version
    else:
        resolved_bump = infer_auto_bump(spec) if bump == "auto" else bump
        new_version = bump_version(spec.version, resolved_bump)

    if new_version in current_versions and new_version != spec.version:
        raise SystemExit(f"Version {new_version} already exists in {spec.registry_index_path}")

    repository_owner = detect_repository_owner()
    if repository_owner and remote_package_version_exists(repository_owner, spec.artifact_id, new_version):
        write_github_output(
            github_output,
            {
                "plugin_id": spec.plugin_id,
                "plugin_name": spec.name,
                "plugin_owner": spec.owner,
                "version": new_version,
                "artifact_id": spec.artifact_id,
                "skipped_existing_package": "true",
                "skip_reason": "existing_github_package",
                "tag_name": spec.release_tag(new_version),
                "release_name": f"{spec.plugin_id} v{new_version}",
            },
        )
        print(
            f"Skipping release {spec.release_tag(new_version)}: "
            f"GitHub Package {spec.artifact_id}:{new_version} already exists"
        )
        return

    if new_version != spec.version:
        write_child_pom_version(spec.pom_path, new_version)
        updated_spec = discover_plugins()[plugin_id]
        write_text(updated_spec.plugin_yaml_path, render_plugin_yaml(updated_spec, new_version))

        next_spec = discover_plugins()[plugin_id]
        next_versions = sorted([*current_versions, new_version], key=semver_key)
        write_text(next_spec.registry_index_path, render_registry_index(next_spec, next_versions))
    else:
        next_spec = spec

    build_command = [
        "mvn",
        "-B",
        "-ntp",
        "-f",
        str(ROOT / "pom.xml"),
        "-pl",
        f":{next_spec.artifact_id}",
        "-am",
    ]
    if new_version == spec.version:
        build_command.append("package")
    else:
        build_command.extend(["-P", "strict", "verify"])

    run_command(*build_command, capture_output=False)

    artifact_path = ROOT / next_spec.dist_artifact_relative_path(new_version)
    if not artifact_path.exists():
        raise SystemExit(f"Expected artifact was not built: {artifact_path}")

    version_path = next_spec.versions_dir / f"{new_version}.yaml"
    if new_version != spec.version:
        published_at = datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")
        source_commit = run_command("git", "rev-parse", "HEAD")
        write_text(version_path, render_registry_version(next_spec, new_version, published_at, source_commit))

    write_github_output(
        github_output,
        {
            "plugin_id": next_spec.plugin_id,
            "plugin_name": next_spec.name,
            "plugin_owner": next_spec.owner,
            "version": new_version,
            "artifact_id": next_spec.artifact_id,
            "module_path": next_spec.module_path,
            "plugin_yaml_path": next_spec.plugin_yaml_path.relative_to(ROOT).as_posix(),
            "pom_path": next_spec.pom_path.relative_to(ROOT).as_posix(),
            "artifact_path": artifact_path.relative_to(ROOT).as_posix(),
            "registry_index_path": next_spec.registry_index_path.relative_to(ROOT).as_posix(),
            "registry_version_path": version_path.relative_to(ROOT).as_posix(),
            "tag_name": next_spec.release_tag(new_version),
            "release_name": f"{next_spec.plugin_id} v{new_version}",
            "skipped_existing_package": "false",
        },
    )

    print(f"Prepared release {next_spec.release_tag(new_version)}")
    print(f"Artifact: {artifact_path.relative_to(ROOT)}")
    print(f"Registry metadata: {version_path.relative_to(ROOT)}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate and release the golemcore-plugins repository")
    subparsers = parser.add_subparsers(dest="command", required=True)

    subparsers.add_parser("validate", help="Validate plugin manifests, module versions, and registry metadata")
    lint_parser = subparsers.add_parser("lint-commits", help="Validate conventional commit subjects in a Git revision range")
    lint_parser.add_argument("--rev-range", required=True, help="Git revision range, e.g. origin/main..HEAD")
    settings_parser = subparsers.add_parser("repo-settings", help="Print repository-level CI settings")
    settings_parser.add_argument(
        "--github-output",
        type=Path,
        help="Optional GitHub Actions output file.",
    )

    release_parser = subparsers.add_parser("release", help="Bump one plugin version, package it, and refresh registry metadata")
    release_parser.add_argument("--plugin", required=True, help="Canonical plugin id, e.g. golemcore/telegram")
    release_parser.add_argument(
        "--bump",
        choices=["auto", "major", "minor", "patch"],
        default="auto",
        help="SemVer bump to apply. 'auto' derives the bump from conventional commits since the last plugin tag.",
    )
    release_parser.add_argument(
        "--version-override",
        help="Explicit release version. When set, --bump is ignored.",
    )
    release_parser.add_argument(
        "--github-output",
        type=Path,
        help="Optional GitHub Actions output file.",
    )
    release_plan_parser = subparsers.add_parser(
        "release-plan",
        help="Print plugin ids that should be released for a given Git revision range.",
    )
    release_plan_parser.add_argument("--rev-range", required=True, help="Git revision range, e.g. <before>..<after>")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.command == "validate":
        validate_repo()
        print("Repository validation passed")
        return 0
    if args.command == "lint-commits":
        lint_commits(args.rev_range)
        return 0
    if args.command == "repo-settings":
        emit_repo_settings(args.github_output)
        return 0
    if args.command == "release":
        run_release(args.plugin, args.bump, args.version_override, args.github_output)
        return 0
    if args.command == "release-plan":
        emit_release_plan(args.rev_range)
        return 0
    raise AssertionError(f"Unsupported command: {args.command}")


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except subprocess.CalledProcessError as error:
        command = " ".join(str(part) for part in error.cmd)
        raise SystemExit(f"Command failed ({error.returncode}): {command}") from error
