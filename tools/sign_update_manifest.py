#!/usr/bin/env python3
"""Sign a sideload update manifest so the app can verify it wasn't tampered.

Dependency-free: RSA operations shell out to the openssl binary (present on
Linux, macOS and Windows-with-Git). The app pins the PUBLIC key (base64 DER,
one line — brand key `brand.updatePublicKey` or NOVAPLAY_UPDATE_PUBLIC_KEY)
and then refuses unsigned or altered manifests. The PRIVATE key stays with
the owner, outside every repository, like the APK signing key.

Usage:
  python tools/sign_update_manifest.py generate-key update-signing.pem
      Writes the private key and prints the single-line public key to pin.

  python tools/sign_update_manifest.py sign update-signing.pem manifest.json \
      [--apk player.apk]
      Fills in apk_sha256 (when --apk is given), signs, and rewrites
      manifest.json with the signature.

The signed payload is exactly:
  "<version_code>\n<version_name>\n<apk_url>\n<apk_sha256>\n<notes>"
built from the raw manifest fields (empty string for absent ones) — matching
UpdateCheckPolicy.signingPayload in the app.
"""

import argparse
import base64
import hashlib
import json
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path


class SigningError(Exception):
    pass


def _openssl() -> str:
    binary = shutil.which("openssl")
    if not binary:
        raise SigningError("openssl not found on PATH; install it or use Git Bash")
    return binary


def _run(*args: str, data: bytes | None = None) -> bytes:
    result = subprocess.run(
        list(args), input=data, capture_output=True, check=False
    )
    if result.returncode != 0:
        raise SigningError(result.stderr.decode(errors="replace").strip() or "openssl failed")
    return result.stdout


def generate_key(private_key_path: Path) -> str:
    """Creates an RSA-2048 private key; returns the single-line public key."""
    openssl = _openssl()
    _run(openssl, "genpkey", "-algorithm", "RSA", "-pkeyopt", "rsa_keygen_bits:2048",
         "-out", str(private_key_path))
    return public_key_line(private_key_path)


def public_key_line(private_key_path: Path) -> str:
    """The pinnable public key: base64 of the X.509/DER encoding, one line."""
    openssl = _openssl()
    der = _run(openssl, "pkey", "-in", str(private_key_path), "-pubout",
               "-outform", "DER")
    return base64.b64encode(der).decode()


def signing_payload(manifest: dict) -> bytes:
    """Must match UpdateCheckPolicy.signingPayload exactly."""
    return "\n".join(
        [
            str(manifest.get("version_code", 0)),
            str(manifest.get("version_name") or ""),
            str(manifest.get("apk_url") or ""),
            str(manifest.get("apk_sha256") or ""),
            str(manifest.get("notes") or ""),
        ]
    ).encode("utf-8")


def sign_manifest(private_key_path: Path, manifest_path: Path, apk_path: Path | None) -> dict:
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if apk_path is not None:
        manifest["apk_sha256"] = hashlib.sha256(apk_path.read_bytes()).hexdigest()
    openssl = _openssl()
    with tempfile.NamedTemporaryFile(delete=False) as payload_file:
        payload_file.write(signing_payload(manifest))
        payload_name = payload_file.name
    try:
        signature = _run(
            openssl, "dgst", "-sha256", "-sign", str(private_key_path), payload_name
        )
    finally:
        Path(payload_name).unlink(missing_ok=True)
    manifest["signature"] = base64.b64encode(signature).decode()
    manifest_path.write_text(
        json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )
    return manifest


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)

    generate = commands.add_parser("generate-key", help="create a signing key pair")
    generate.add_argument("private_key", type=Path)

    sign = commands.add_parser("sign", help="sign a manifest in place")
    sign.add_argument("private_key", type=Path)
    sign.add_argument("manifest", type=Path)
    sign.add_argument("--apk", type=Path, default=None,
                      help="APK to hash into apk_sha256 before signing")

    args = parser.parse_args(argv)
    try:
        if args.command == "generate-key":
            if args.private_key.exists():
                raise SigningError(f"{args.private_key} already exists; refusing to overwrite")
            public = generate_key(args.private_key)
            print(f"Private key written to {args.private_key} — keep it OUTSIDE the repository.")
            print("Pin this public key (brand.updatePublicKey / NOVAPLAY_UPDATE_PUBLIC_KEY):")
            print(public)
        else:
            manifest = sign_manifest(args.private_key, args.manifest, args.apk)
            print(f"Signed {args.manifest} (version_code={manifest.get('version_code')}).")
    except SigningError as failure:
        print(f"error: {failure}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
