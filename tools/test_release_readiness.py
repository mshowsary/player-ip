from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import package_release  # noqa: E402
import release_readiness  # noqa: E402


class ReleaseReadinessTest(unittest.TestCase):
    def _create_package(
        self,
        root: Path,
        *,
        signed: bool = False,
        portal: bool = False,
    ) -> tuple[Path, str]:
        metadata = root / "release-metadata.properties"
        metadata.write_text(
            "\n".join(
                [
                    "applicationId=com.novaplay.tv",
                    "versionCode=1000001",
                    "versionName=1.0.0-rc.1",
                    "buildChannel=production",
                    f"portalConfigured={str(portal).lower()}",
                    f"signingConfigured={str(signed).lower()}",
                ]
            )
            + "\n",
            encoding="utf-8",
        )
        apk_dir = root / "apk"
        aab_dir = root / "aab"
        apk_dir.mkdir()
        aab_dir.mkdir()
        (apk_dir / "app-release.apk").write_bytes(b"release-apk")
        (aab_dir / "app-release.aab").write_bytes(b"release-aab")
        output = root / "package"
        source_commit = "0123456789abcdef0123456789abcdef01234567"
        package_release.package_release(
            metadata_path=metadata,
            apk_dir=apk_dir,
            aab_dir=aab_dir,
            output_dir=output,
            commit=source_commit,
            build_commit="89abcdef0123456789abcdef0123456789abcdef",
        )
        return output, source_commit

    def test_valid_package_checks_artifacts_checksums_and_commit(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            package_dir, source_commit = self._create_package(Path(temporary))

            manifest, file_count = release_readiness.validate_release_package(
                package_dir,
                expected_commit=source_commit,
            )

            self.assertEqual(4, file_count)
            self.assertEqual(source_commit, manifest["commit"])
            self.assertEqual({"apk", "aab"}, {item["kind"] for item in manifest["artifacts"]})

    def test_tampered_artifact_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            package_dir, source_commit = self._create_package(Path(temporary))
            apk = next(package_dir.glob("*.apk"))
            apk.write_bytes(apk.read_bytes() + b"tampered")

            with self.assertRaisesRegex(release_readiness.ReadinessError, "Checksum mismatch"):
                release_readiness.validate_release_package(
                    package_dir,
                    expected_commit=source_commit,
                )

    def test_unexpected_package_file_is_rejected(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            package_dir, source_commit = self._create_package(Path(temporary))
            (package_dir / "notes-with-unreviewed-content.txt").write_text("extra", encoding="utf-8")

            with self.assertRaisesRegex(release_readiness.ReadinessError, "unexpected files"):
                release_readiness.validate_release_package(
                    package_dir,
                    expected_commit=source_commit,
                )

    def test_publish_requirements_are_explicit(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            package_dir, source_commit = self._create_package(Path(temporary))

            with self.assertRaisesRegex(release_readiness.ReadinessError, "externally signed"):
                release_readiness.validate_release_package(
                    package_dir,
                    expected_commit=source_commit,
                    require_signed=True,
                )
            with self.assertRaisesRegex(release_readiness.ReadinessError, "configured portal"):
                release_readiness.validate_release_package(
                    package_dir,
                    expected_commit=source_commit,
                    require_portal=True,
                )

    def test_repository_hygiene_rejects_tracked_signing_material(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            subprocess.run(["git", "init", "-q", str(root)], check=True)
            (root / "release.jks").write_bytes(b"not-a-real-keystore")
            subprocess.run(["git", "-C", str(root), "add", "release.jks"], check=True)

            with self.assertRaisesRegex(release_readiness.ReadinessError, "signing binary"):
                release_readiness.validate_repository_hygiene(root)

    def test_repository_hygiene_rejects_high_confidence_private_key(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            subprocess.run(["git", "init", "-q", str(root)], check=True)
            (root / "secret.txt").write_text(
                "-----BEGIN PRIVATE KEY-----\nplaceholder\n-----END PRIVATE KEY-----\n",
                encoding="utf-8",
            )
            subprocess.run(["git", "-C", str(root), "add", "secret.txt"], check=True)

            with self.assertRaisesRegex(release_readiness.ReadinessError, "private_key"):
                release_readiness.validate_repository_hygiene(root)

    def test_readiness_report_contains_only_safe_summary_fields(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            package_dir, source_commit = self._create_package(root)
            manifest, package_file_count = release_readiness.validate_release_package(
                package_dir,
                expected_commit=source_commit,
            )
            report = release_readiness.build_report(
                manifest=manifest,
                tracked_count=123,
                package_file_count=package_file_count,
            )
            report_path = root / "release-readiness.json"
            release_readiness.write_report(report_path, report)
            parsed = json.loads(report_path.read_text(encoding="utf-8"))

            self.assertTrue(parsed["ready"])
            self.assertEqual(source_commit, parsed["source_commit"])
            combined = report_path.read_text(encoding="utf-8").lower()
            for forbidden in (
                "password",
                "access_token",
                "refresh_token",
                "portal_url",
                "device_id",
                "keystore",
                "key_alias",
            ):
                self.assertNotIn(forbidden, combined)


if __name__ == "__main__":
    unittest.main()
