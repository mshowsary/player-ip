from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import package_release  # noqa: E402


class ReleasePackageTest(unittest.TestCase):
    def test_metadata_validation_rejects_bad_version(self) -> None:
        with self.assertRaisesRegex(ValueError, "versionName"):
            package_release.parse_metadata(
                {
                    "applicationId": "com.novaplay.tv",
                    "versionCode": "1000001",
                    "versionName": "release candidate one",
                    "buildChannel": "production",
                    "portalConfigured": "false",
                    "signingConfigured": "false",
                }
            )

    def test_package_is_deterministic_and_privacy_safe(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            metadata = root / "release-metadata.properties"
            metadata.write_text(
                "\n".join(
                    [
                        "applicationId=com.novaplay.tv",
                        "versionCode=1000001",
                        "versionName=1.0.0-rc.1",
                        "buildChannel=production",
                        "portalConfigured=false",
                        "signingConfigured=false",
                    ]
                )
                + "\n",
                encoding="utf-8",
            )
            apk_dir = root / "apk"
            aab_dir = root / "aab"
            apk_dir.mkdir()
            aab_dir.mkdir()
            (apk_dir / "app-release-unsigned.apk").write_bytes(b"fake-apk")
            (aab_dir / "app-release.aab").write_bytes(b"fake-aab")

            first_output = root / "first"
            second_output = root / "second"
            commit = "0123456789abcdef0123456789abcdef01234567"

            first_manifest = package_release.package_release(
                metadata_path=metadata,
                apk_dir=apk_dir,
                aab_dir=aab_dir,
                output_dir=first_output,
                commit=commit,
            )
            second_manifest = package_release.package_release(
                metadata_path=metadata,
                apk_dir=apk_dir,
                aab_dir=aab_dir,
                output_dir=second_output,
                commit=commit,
            )

            self.assertEqual(first_manifest, second_manifest)
            self.assertEqual(
                (first_output / "release-manifest.json").read_bytes(),
                (second_output / "release-manifest.json").read_bytes(),
            )
            self.assertEqual(
                (first_output / "SHA256SUMS").read_bytes(),
                (second_output / "SHA256SUMS").read_bytes(),
            )

            parsed = json.loads(
                (first_output / "release-manifest.json").read_text(encoding="utf-8")
            )
            self.assertEqual("1.0.0-rc.1", parsed["version_name"])
            self.assertEqual(1000001, parsed["version_code"])
            self.assertFalse(parsed["portal_configured"])
            self.assertFalse(parsed["signing_configured"])
            self.assertEqual(2, len(parsed["artifacts"]))

            combined_text = "\n".join(
                path.read_text(encoding="utf-8")
                for path in (
                    first_output / "release-manifest.json",
                    first_output / "SHA256SUMS",
                )
            ).lower()
            for forbidden in (
                "password=",
                "refresh_token",
                "access_token",
                "portal.example.com",
                "username=",
            ):
                self.assertNotIn(forbidden, combined_text)

    def test_multiple_apks_fail_instead_of_packaging_ambiguously(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            (directory / "one.apk").write_bytes(b"one")
            (directory / "two.apk").write_bytes(b"two")
            with self.assertRaisesRegex(ValueError, "exactly one"):
                package_release.find_single_artifact(directory, ".apk")


if __name__ == "__main__":
    unittest.main()
