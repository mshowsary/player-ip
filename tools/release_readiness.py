#!/usr/bin/env python3
"""Validate NovaPlay release-package integrity and repository release hygiene.

The checks are intentionally dependency-free so they run the same way in local
PowerShell, Linux CI and protected release environments. The script never reads
signing passwords, provider credentials or playlist data. Its JSON report stores
only boolean results, counts and non-sensitive release metadata.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
from dataclasses import dataclass, asdict
from pathlib import Path
from typing import Iterable, Mapping, Sequence

SHA256_PATTERN = re.compile(r"^[0-9a-f]{64}$")
GIT_SHA_PATTERN = re.compile(r"^[0-9a-f]{7,40}$")
VERSION_PATTERN = re.compile(r"^\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?$")

FORBIDDEN_TRACKED_SUFFIXES = {
    ".apk",
    ".aab",
    ".jks",
    ".keystore",
    ".p12",
    ".pfx",
    ".der",
}
FORBIDDEN_TRACKED_NAMES = {
    ".env",
    "local.properties",
    "google-services.json",
}
FORBIDDEN_TRACKED_PREFIXES = (
    "dist/",
    "app/build/",
    "build/",
    ".gradle/",
)
HIGH_CONFIDENCE_SECRET_PATTERNS = {
    "private_key": re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
    "aws_access_key": re.compile(r"\bAKIA[0-9A-Z]{16}\b"),
    "github_token": re.compile(r"\bgh[pousr]_[A-Za-z0-9]{30,}\b"),
    "stripe_live_key": re.compile(r"\bsk_live_[A-Za-z0-9]{20,}\b"),
}
SENSITIVE_MANIFEST_KEYS = {
    "password",
    "username",
    "access_token",
    "refresh_token",
    "device_id",
    "device_key",
    "mac",
    "portal_url",
    "portal_host",
    "keystore",
    "key_alias",
    "store_password",
    "key_password",
}
EXPECTED_PACKAGE_SUPPORT_FILES = {"release-manifest.json", "SHA256SUMS"}
EXPECTED_ARTIFACT_KINDS = {"apk", "aab"}


class ReadinessError(ValueError):
    """Raised when a release-readiness requirement is not met."""


@dataclass(frozen=True)
class ReadinessReport:
    schema_version: int
    ready: bool
    source_commit: str
    version_name: str
    version_code: int
    build_channel: str
    portal_configured: bool
    signing_configured: bool
    tracked_files_checked: int
    package_files_checked: int
    artifact_count: int
    checks: Mapping[str, bool]


def _run_git(repository: Path, *args: str) -> str:
    try:
        return subprocess.check_output(
            ["git", "-C", str(repository), *args],
            text=True,
            stderr=subprocess.DEVNULL,
        ).strip()
    except (OSError, subprocess.CalledProcessError) as error:
        raise ReadinessError("Repository is not an accessible Git worktree") from error


def tracked_files(repository: Path) -> list[str]:
    output = _run_git(repository, "ls-files", "-z")
    return [value for value in output.split("\0") if value]


def current_commit(repository: Path) -> str:
    value = _run_git(repository, "rev-parse", "HEAD").lower()
    if not GIT_SHA_PATTERN.fullmatch(value):
        raise ReadinessError("Current Git commit is invalid")
    return value


def _read_text_if_small(path: Path, limit_bytes: int = 2_000_000) -> str | None:
    try:
        if path.stat().st_size > limit_bytes:
            return None
        return path.read_text(encoding="utf-8")
    except (OSError, UnicodeDecodeError):
        return None


def validate_repository_hygiene(repository: Path) -> int:
    files = tracked_files(repository)
    violations: list[str] = []

    for relative in files:
        normalized = relative.replace("\\", "/")
        path = repository / relative
        suffix = path.suffix.lower()
        basename = path.name.lower()

        if suffix in FORBIDDEN_TRACKED_SUFFIXES:
            violations.append(f"tracked release/signing binary: {normalized}")
        if basename in FORBIDDEN_TRACKED_NAMES:
            violations.append(f"tracked local secret/config file: {normalized}")
        if normalized.startswith(FORBIDDEN_TRACKED_PREFIXES):
            violations.append(f"tracked generated output: {normalized}")

        text = _read_text_if_small(path)
        if text is None:
            continue
        for label, pattern in HIGH_CONFIDENCE_SECRET_PATTERNS.items():
            if pattern.search(text):
                violations.append(f"high-confidence {label} material: {normalized}")

    if violations:
        raise ReadinessError("Repository hygiene failed:\n- " + "\n- ".join(sorted(set(violations))))
    return len(files)


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def parse_checksums(path: Path) -> dict[str, str]:
    checksums: dict[str, str] = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line:
            continue
        parts = line.split(maxsplit=1)
        if len(parts) != 2:
            raise ReadinessError("SHA256SUMS contains an invalid line")
        digest, filename = parts
        filename = filename.lstrip("*").strip()
        digest = digest.lower()
        if not SHA256_PATTERN.fullmatch(digest):
            raise ReadinessError("SHA256SUMS contains an invalid hash")
        if Path(filename).name != filename:
            raise ReadinessError("SHA256SUMS may only reference package-local filenames")
        if filename in checksums:
            raise ReadinessError("SHA256SUMS contains a duplicate filename")
        checksums[filename] = digest
    return checksums


def _validate_manifest_shape(manifest: Mapping[str, object]) -> None:
    required = {
        "schema_version",
        "application_id",
        "version_code",
        "version_name",
        "commit",
        "build_commit",
        "build_channel",
        "portal_configured",
        "signing_configured",
        "artifacts",
    }
    missing = sorted(required - set(manifest))
    if missing:
        raise ReadinessError("Release manifest is missing: " + ", ".join(missing))

    if manifest["schema_version"] != 1:
        raise ReadinessError("Unsupported release manifest schema")
    if not isinstance(manifest["version_code"], int) or manifest["version_code"] < 1:
        raise ReadinessError("Release manifest version_code is invalid")
    if not isinstance(manifest["version_name"], str) or not VERSION_PATTERN.fullmatch(manifest["version_name"]):
        raise ReadinessError("Release manifest version_name is invalid")
    for key in ("commit", "build_commit"):
        value = manifest[key]
        if not isinstance(value, str) or not GIT_SHA_PATTERN.fullmatch(value):
            raise ReadinessError(f"Release manifest {key} is invalid")
    for key in ("portal_configured", "signing_configured"):
        if not isinstance(manifest[key], bool):
            raise ReadinessError(f"Release manifest {key} must be boolean")
    if manifest["build_channel"] != "production":
        raise ReadinessError("Release manifest build_channel must be production")

    lowered_keys = {str(key).lower() for key in manifest}
    leaked = sorted(lowered_keys & SENSITIVE_MANIFEST_KEYS)
    if leaked:
        raise ReadinessError("Release manifest contains sensitive fields: " + ", ".join(leaked))


def validate_release_package(
    package_dir: Path,
    *,
    expected_commit: str | None = None,
    require_signed: bool = False,
    require_portal: bool = False,
) -> tuple[dict[str, object], int]:
    if not package_dir.is_dir():
        raise ReadinessError(f"Release package directory does not exist: {package_dir}")

    files = sorted(path for path in package_dir.iterdir() if path.is_file())
    names = {path.name for path in files}
    if not EXPECTED_PACKAGE_SUPPORT_FILES.issubset(names):
        raise ReadinessError("Release package is missing manifest or checksum file")

    manifest = json.loads((package_dir / "release-manifest.json").read_text(encoding="utf-8"))
    if not isinstance(manifest, dict):
        raise ReadinessError("Release manifest root must be an object")
    _validate_manifest_shape(manifest)

    if expected_commit is not None and manifest["commit"] != expected_commit.lower():
        raise ReadinessError("Release package source commit does not match the tested branch commit")
    if require_signed and not manifest["signing_configured"]:
        raise ReadinessError("A distributable release must be externally signed")
    if require_portal and not manifest["portal_configured"]:
        raise ReadinessError("A managed-provider release requires a configured portal")

    artifacts = manifest["artifacts"]
    if not isinstance(artifacts, list) or len(artifacts) != 2:
        raise ReadinessError("Release manifest must describe exactly one APK and one AAB")

    checksums = parse_checksums(package_dir / "SHA256SUMS")
    seen_kinds: set[str] = set()
    artifact_names: set[str] = set()
    source_prefix = str(manifest["commit"])[:12]
    signature_marker = "signed" if manifest["signing_configured"] else "unsigned"

    for item in artifacts:
        if not isinstance(item, dict):
            raise ReadinessError("Release manifest artifact entry must be an object")
        kind = item.get("kind")
        filename = item.get("filename")
        digest = item.get("sha256")
        size_bytes = item.get("size_bytes")
        if kind not in EXPECTED_ARTIFACT_KINDS or kind in seen_kinds:
            raise ReadinessError("Release manifest artifact kinds must be one APK and one AAB")
        if not isinstance(filename, str) or Path(filename).name != filename:
            raise ReadinessError("Release manifest artifact filename is invalid")
        expected_suffix = f".{kind}"
        if not filename.endswith(expected_suffix):
            raise ReadinessError("Release manifest artifact extension does not match its kind")
        if source_prefix not in filename or signature_marker not in filename:
            raise ReadinessError("Release artifact filename is not traceable to source/signing state")
        if not isinstance(digest, str) or not SHA256_PATTERN.fullmatch(digest.lower()):
            raise ReadinessError("Release manifest artifact hash is invalid")
        if not isinstance(size_bytes, int) or size_bytes <= 0:
            raise ReadinessError("Release manifest artifact size is invalid")

        artifact_path = package_dir / filename
        if not artifact_path.is_file():
            raise ReadinessError(f"Release artifact is missing: {filename}")
        actual_digest = sha256_file(artifact_path)
        if actual_digest != digest.lower() or checksums.get(filename) != actual_digest:
            raise ReadinessError(f"Checksum mismatch for {filename}")
        if artifact_path.stat().st_size != size_bytes:
            raise ReadinessError(f"Size mismatch for {filename}")
        seen_kinds.add(kind)
        artifact_names.add(filename)

    expected_names = EXPECTED_PACKAGE_SUPPORT_FILES | artifact_names
    unexpected = sorted(names - expected_names)
    missing_checksums = sorted(artifact_names - set(checksums))
    extra_checksums = sorted(set(checksums) - artifact_names)
    if unexpected:
        raise ReadinessError("Release package contains unexpected files: " + ", ".join(unexpected))
    if missing_checksums or extra_checksums:
        raise ReadinessError("SHA256SUMS does not match the manifest artifact list")

    return manifest, len(files)


def build_report(
    *,
    manifest: Mapping[str, object],
    tracked_count: int,
    package_file_count: int,
) -> ReadinessReport:
    return ReadinessReport(
        schema_version=1,
        ready=True,
        source_commit=str(manifest["commit"]),
        version_name=str(manifest["version_name"]),
        version_code=int(manifest["version_code"]),
        build_channel=str(manifest["build_channel"]),
        portal_configured=bool(manifest["portal_configured"]),
        signing_configured=bool(manifest["signing_configured"]),
        tracked_files_checked=tracked_count,
        package_files_checked=package_file_count,
        artifact_count=len(manifest["artifacts"]),
        checks={
            "repository_hygiene": True,
            "manifest_schema": True,
            "source_commit_match": True,
            "artifact_set_exact": True,
            "checksums_match": True,
            "metadata_privacy": True,
        },
    )


def write_report(path: Path, report: ReadinessReport) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(asdict(report), indent=2, sort_keys=True) + "\n", encoding="utf-8")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", type=Path, default=Path("."))
    parser.add_argument("--package-dir", type=Path, default=Path("dist/release-candidate"))
    parser.add_argument("--expected-commit", default=None)
    parser.add_argument("--require-signed", action="store_true")
    parser.add_argument("--require-portal", action="store_true")
    parser.add_argument(
        "--report",
        type=Path,
        default=Path("dist/release-readiness/release-readiness.json"),
    )
    return parser


def main(argv: Iterable[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    repository = args.repository.resolve()
    expected_commit = args.expected_commit or current_commit(repository)
    tracked_count = validate_repository_hygiene(repository)
    manifest, package_file_count = validate_release_package(
        args.package_dir.resolve(),
        expected_commit=expected_commit,
        require_signed=args.require_signed,
        require_portal=args.require_portal,
    )
    report = build_report(
        manifest=manifest,
        tracked_count=tracked_count,
        package_file_count=package_file_count,
    )
    write_report(args.report.resolve(), report)
    print(
        f"NovaPlay {report.version_name} readiness passed for {report.source_commit[:12]} "
        f"({report.artifact_count} artifacts, signed={report.signing_configured}, "
        f"portal={report.portal_configured})"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
