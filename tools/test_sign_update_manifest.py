"""Signing-tool tests; skipped where openssl is unavailable."""

import base64
import json
import shutil
import tempfile
import unittest
from pathlib import Path

import sign_update_manifest as tool


@unittest.skipIf(shutil.which("openssl") is None, "openssl not on PATH")
class SignUpdateManifestTest(unittest.TestCase):
    def setUp(self):
        self.workdir = Path(tempfile.mkdtemp())
        self.key = self.workdir / "signing.pem"
        self.public = tool.generate_key(self.key)

    def tearDown(self):
        shutil.rmtree(self.workdir, ignore_errors=True)

    def write_manifest(self, **overrides) -> Path:
        manifest = {
            "version_code": 1000002,
            "version_name": "1.0.1",
            "apk_url": "https://updates.example.com/player-1.0.1.apk",
            "notes": "Fixes",
        }
        manifest.update(overrides)
        path = self.workdir / "manifest.json"
        path.write_text(json.dumps(manifest), encoding="utf-8")
        return path

    def test_public_key_is_single_line_base64_der(self):
        self.assertNotIn("\n", self.public)
        der = base64.b64decode(self.public)
        self.assertGreater(len(der), 200)  # RSA-2048 SPKI is ~294 bytes.

    def test_sign_adds_verifiable_signature_and_apk_hash(self):
        apk = self.workdir / "app.apk"
        apk.write_bytes(b"fake apk bytes")
        path = self.write_manifest()
        signed = tool.sign_manifest(self.key, path, apk)
        self.assertIn("signature", signed)
        self.assertEqual(len(signed["apk_sha256"]), 64)
        # Verify with openssl against the public key, exactly like the app.
        payload = self.workdir / "payload.bin"
        payload.write_bytes(tool.signing_payload(signed))
        signature = self.workdir / "sig.bin"
        signature.write_bytes(base64.b64decode(signed["signature"]))
        public_der = self.workdir / "public.der"
        public_der.write_bytes(base64.b64decode(self.public))
        tool._run(
            tool._openssl(), "dgst", "-sha256",
            "-verify", str(public_der), "-keyform", "DER",
            "-signature", str(signature), str(payload),
        )

    def test_payload_matches_the_app_contract(self):
        manifest = {
            "version_code": 7,
            "version_name": "2.0",
            "apk_url": "https://x/y.apk",
            "apk_sha256": "abc",
            "notes": "",
        }
        self.assertEqual(
            tool.signing_payload(manifest),
            b"7\n2.0\nhttps://x/y.apk\nabc\n",
        )

    def test_tampering_breaks_verification(self):
        path = self.write_manifest()
        signed = tool.sign_manifest(self.key, path, None)
        signed["apk_url"] = "https://evil.example.com/malware.apk"
        payload = self.workdir / "payload.bin"
        payload.write_bytes(tool.signing_payload(signed))
        signature = self.workdir / "sig.bin"
        signature.write_bytes(base64.b64decode(signed["signature"]))
        public_der = self.workdir / "public.der"
        public_der.write_bytes(base64.b64decode(self.public))
        with self.assertRaises(tool.SigningError):
            tool._run(
                tool._openssl(), "dgst", "-sha256",
                "-verify", str(public_der), "-keyform", "DER",
                "-signature", str(signature), str(payload),
            )


if __name__ == "__main__":
    unittest.main()
