#!/usr/bin/env python3
"""Build a privacy-safe NovaPlay release-candidate package.

The Android build remains responsible for compilation and signing. This tool
copies exactly one release APK and AAB into a clean directory, gives them stable
names, computes SHA-256 checksums, and writes a deterministic JSON manifest.
It never reads or emits signing passwords, provider URLs, tokens, or playlist
credentials.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import shutil
import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, Mapping

VERSION_NAME_PATTERN = re.compile(r"^\d+\.\d+\.\d+(?:[-+][0-9A-Za-z.-]+)?$")
COMMIT_PATTERN = re.compile(r"^[0-9a-fA-F]{7,40}$")


@dataclass(frozen=True)
class ReleaseMetadata:
    application_id: str
    version_code: int
    version_name: str
    build_channel: str
    portal_configured: bool
    signing_configured: bool


def read_properties(path: Path) -> dict[str, str]:
    """Read the small Java-properties subset emitted by Gradle."""
    values: dict[str, str] = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            raise ValueError(f"Invalid metadata line: {raw_line!r}")
        key, value = line.split("=", 1)
        key = key.strip()
        if not key:
            raise ValueError("Release metadata contains an empty key")
        values[key] = value.strip()
    return values


def _required(values: Mapping[str, str], key: str) -> str:
    value = values.get(key, "").strip()
    if not value:
        raise ValueError(f"Release metadata is missing {key}")
    return value


def _parse_bool(value: str, key: str) -> bool:
    normalized = value.strip().lower()
    if normalized == "true":
        return True
    if normalized == "false":
        return False
    raise ValueError(f"Release metadata {key} must be true or false")


def parse_metadata(values: Mapping[str, str]) -> ReleaseMetadata:
    version_code_text = _required(values, "versionCode")
    try:
        version_code = int(version_code_text)
    except ValueError as error:
        raise ValueError("Release metadata versionCode must be an integer") from error
    if not 1 <= version_code <= 2_100_000_000:
        raise ValueError("Release metadata versionCode is outside Android's supported range")

    version_name = _required(values, "versionName")
    if not VERSION_NAME_PATTERN.fullmatch(version_name):
        raise ValueError("Release metadata versionName is not semantic")

    application_id = _required(values, "applicationId")
    if not re.fullmatch(r"[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+", application_id):
        raise ValueError("Release metadata applicationId is invalid")

    return ReleaseMetadata(
        application_id=application_id,
        version_code=version_code,
        version_name=version_name,
        build_channel=_required(values, "buildChannel"),
        portal_configured=_parse_bool(_required(values, "portalConfigured"), "portalConfigured"),
        signing_configured=_parse_bool(_required(values, "signingConfigured"), "signingConfigured"),
    )


def find_single_artifact(directory: Path, suffix: str) -> Path:
    candidates = sorted(
        path for path in directory.glob(f"*{suffix}") if path.is_file()
    )
    if len(candidates) != 1:
        raise ValueError(
            f"Expected exactly one {suffix} in {directory}, found {len(candidates)}"
        )
    return candidates[0]


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def resolve_commit(explicit: str | None) -> str:
    candidate = (explicit or "").strip()
    if not candidate:
        try:
            candidate = subprocess.check_output(
                ["git", "rev-parse", "HEAD"],
                text=True,
                stderr=subprocess.DEVNULL,
            ).strip()
        except (OSError, subprocess.CalledProcessError):
            candidate = "unknown"
    if candidate != "unknown" and not COMMIT_PATTERN.fullmatch(candidate):
        raise ValueError("Commit must be a 7-40 character hexadecimal Git SHA")
    return candidate.lower()


def _safe_filename_part(value: str) -> str:
    cleaned = re.sub(r"[^0-9A-Za-z._-]+", "-", value).strip("-.")
    if not cleaned:
        raise ValueError("Release filename component became empty")
    return cleaned


def package_release(
    *,
    metadata_path: Path,
    apk_dir: Path,
    aab_dir: Path,
    output_dir: Path,
    commit: str | None,
    build_commit: str | None = None,
) -> dict[str, object]:
    metadata = parse_metadata(read_properties(metadata_path))
    source_commit = resolve_commit(commit)
    tested_build_commit = resolve_commit(build_commit) if build_commit else source_commit
    apk_source = find_single_artifact(apk_dir, ".apk")
    aab_source = find_single_artifact(aab_dir, ".aab")

    if output_dir.exists():
        shutil.rmtree(output_dir)
    output_dir.mkdir(parents=True, exist_ok=False)

    version_part = _safe_filename_part(metadata.version_name)
    commit_part = source_commit[:12] if source_commit != "unknown" else "unknown"
    signature_part = "signed" if metadata.signing_configured else "unsigned"
    base_name = f"novaplay-{version_part}-{metadata.version_code}-{commit_part}-{signature_part}"

    copied: list[tuple[str, Path]] = []
    for kind, source, suffix in (
        ("apk", apk_source, ".apk"),
        ("aab", aab_source, ".aab"),
    ):
        destination = output_dir / f"{base_name}{suffix}"
        shutil.copyfile(source, destination)
        copied.append((kind, destination))

    artifacts: list[dict[str, object]] = []
    checksum_lines: list[str] = []
    for kind, path in copied:
        checksum = sha256_file(path)
        artifacts.append(
            {
                "kind": kind,
                "filename": path.name,
                "sha256": checksum,
                "size_bytes": path.stat().st_size,
            }
        )
        checksum_lines.append(f"{checksum}  {path.name}")

    manifest: dict[str, object] = {
        "schema_version": 1,
        "application_id": metadata.application_id,
        "version_code": metadata.version_code,
        "version_name": metadata.version_name,
        # commit is the exact branch/source commit collaborators approve. A PR
        # workflow may compile GitHub's synthetic merge commit, recorded separately.
        "commit": source_commit,
        "build_commit": tested_build_commit,
        "build_channel": metadata.build_channel,
        "portal_configured": metadata.portal_configured,
        "signing_configured": metadata.signing_configured,
        "artifacts": artifacts,
    }
    (output_dir / "release-manifest.json").write_text(
        json.dumps(manifest, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    (output_dir / "SHA256SUMS").write_text(
        "\n".join(sorted(checksum_lines)) + "\n",
        encoding="utf-8",
    )
    return manifest


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--metadata",
        type=Path,
        default=Path("app/build/release-candidate/release-metadata.properties"),
    )
    parser.add_argument(
        "--apk-dir",
        type=Path,
        default=Path("app/build/outputs/apk/release"),
    )
    parser.add_argument(
        "--aab-dir",
        type=Path,
        default=Path("app/build/outputs/bundle/release"),
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("dist/release-candidate"),
    )
    parser.add_argument(
        "--commit",
        default=None,
        help="Exact source/head commit being approved; defaults to local HEAD.",
    )
    parser.add_argument(
        "--build-commit",
        default=None,
        help="Optional CI merge/test commit when it differs from the source commit.",
    )
    return parser


def main(argv: Iterable[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    manifest = package_release(
        metadata_path=args.metadata,
        apk_dir=args.apk_dir,
        aab_dir=args.aab_dir,
        output_dir=args.output,
        commit=args.commit,
        build_commit=args.build_commit,
    )
    print(
        f"Packaged NovaPlay {manifest['version_name']} "
        f"({manifest['version_code']}) at {args.output}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
