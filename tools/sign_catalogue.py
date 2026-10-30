#!/usr/bin/env python3
"""Tool to generate and sign Arogyasathi Voice Pack Catalogues with ECDSA NIST P-256.

Usage:
  python tools/sign_catalogue.py --generate-keys --key-dir keys/
  python tools/sign_catalogue.py --packs-dir build/voice-packs/ --base-url "https://assets.arogyasathi.org/voice-packs/" --key keys/catalogue_private_key.pem --out build/voice-packs/catalogue.signed.json
"""

from __future__ import annotations

import argparse
import base64
import datetime
import json
from pathlib import Path
import sys

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec


KEY_ID = "arogyasathi-release-2026"
ALGORITHM = "SHA256withECDSA"


def generate_keypair(key_dir: Path) -> tuple[Path, Path, str]:
    """Generates an ECDSA NIST P-256 keypair and saves PEM + Base64 public key."""
    key_dir.mkdir(parents=True, exist_ok=True)
    priv_path = key_dir / "catalogue_private_key.pem"
    pub_path = key_dir / "catalogue_public_key.b64"

    private_key = ec.generate_private_key(ec.SECP256R1())
    # Save private key PEM
    with priv_path.open("wb") as f:
        f.write(
            private_key.private_bytes(
                encoding=serialization.Encoding.PEM,
                format=serialization.PrivateFormat.PKCS8,
                encryption_algorithm=serialization.NoEncryption(),
            )
        )

    # Export public key as Base64-encoded X.509 DER
    public_key = private_key.public_key()
    der_public_key = public_key.public_bytes(
        encoding=serialization.Encoding.DER,
        format=serialization.PublicFormat.SubjectPublicKeyInfo,
    )
    b64_public_key = base64.b64encode(der_public_key).decode("ascii")
    pub_path.write_text(b64_public_key + "\n", encoding="utf-8")

    return priv_path, pub_path, b64_public_key


def load_private_key(priv_path: Path) -> ec.EllipticCurvePrivateKey:
    with priv_path.open("rb") as f:
        key = serialization.load_pem_private_key(f.read(), password=None)
        if not isinstance(key, ec.EllipticCurvePrivateKey):
            raise ValueError(f"Key in {priv_path} is not an EllipticCurvePrivateKey")
        return key


def sign_catalogue(
    catalogue_dict: dict,
    private_key: ec.EllipticCurvePrivateKey,
    key_id: str = KEY_ID,
) -> dict:
    """Signs a canonical JSON string using SHA256withECDSA."""
    payload_str = json.dumps(catalogue_dict, separators=(",", ":"), sort_keys=True)
    payload_bytes = payload_str.encode("utf-8")
    der_signature = private_key.sign(payload_bytes, ec.ECDSA(hashes.SHA256()))
    sig_b64 = base64.b64encode(der_signature).decode("ascii")

    envelope = {
        "payload": payload_str,
        "signature": sig_b64,
        "algorithm": ALGORITHM,
        "keyId": key_id,
    }
    return envelope


def build_catalogue_from_packs(packs_dir: Path, base_url: str) -> dict:
    """Discovers companion manifests (*.pack.json) in packs_dir and builds a catalogue."""
    packs = []
    base_url = base_url.rstrip("/") + "/"
    for manifest_path in sorted(packs_dir.glob("*.pack.json")):
        try:
            data = json.loads(manifest_path.read_text(encoding="utf-8"))
            pack_id = data["packId"]
            lang_tag = data["languageTag"]
            version = data.get("model", {}).get("version", "1.0.0")
            archive_sha256 = data["archiveSha256"]
            
            # Find matching zip archive
            zip_filename = f"{pack_id}-{version}.zip"
            zip_path = packs_dir / zip_filename
            if not zip_path.exists():
                zip_filename = manifest_path.stem.replace(".pack", "") + ".zip"
                zip_path = packs_dir / zip_filename

            if not zip_path.exists():
                print(f"Warning: No zip found for manifest {manifest_path.name}", file=sys.stderr)
                continue

            archive_bytes = zip_path.stat().st_size
            download_url = f"{base_url}{zip_path.name}"

            packs.append({
                "packId": pack_id,
                "languageTag": lang_tag,
                "displayName": data.get("displayName", lang_tag),
                "version": str(version),
                "downloadUrl": download_url,
                "bytes": archive_bytes,
                "sha256": archive_sha256.lower(),
                "minAppVersionCode": 1,
                "runtime": f"{data.get('runtime', {}).get('engine', 'sherpa-onnx')}-{data.get('runtime', {}).get('minimumRuntimeVersion', '2.0.1')}",
            })
        except Exception as e:
            print(f"Error parsing {manifest_path}: {e}", file=sys.stderr)

    return {
        "schemaVersion": 1,
        "publishedAt": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "minAppVersionCode": 1,
        "packs": packs,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Sign Arogyasathi voice pack catalogue.")
    parser.add_argument("--generate-keys", action="store_true", help="Generate a new ECDSA P-256 keypair.")
    parser.add_argument("--key-dir", type=Path, default=Path("keys"), help="Directory for key storage.")
    parser.add_argument("--key", type=Path, help="Path to PEM private key.")
    parser.add_argument("--packs-dir", type=Path, help="Directory containing .pack.json files.")
    parser.add_argument("--base-url", type=str, default="https://assets.arogyasathi.org/voice-packs/", help="Base URL for downloads.")
    parser.add_argument("--out", type=Path, help="Output path for signed catalogue envelope JSON.")
    args = parser.parse_args()

    if args.generate_keys:
        priv_p, pub_p, b64_pub = generate_keypair(args.key_dir)
        print("Generated ECDSA NIST P-256 Keypair:")
        print(f"  Private Key: {priv_p}")
        print(f"  Public Key:  {pub_p}")
        print(f"  Public Key (Base64 X.509 DER):\n{b64_pub}")
        return 0

    if not args.key or not args.packs_dir or not args.out:
        parser.print_help()
        return 1

    private_key = load_private_key(args.key)
    catalogue = build_catalogue_from_packs(args.packs_dir, args.base_url)
    envelope = sign_catalogue(catalogue, private_key)

    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(envelope, indent=2) + "\n", encoding="utf-8")
    print(f"Successfully wrote signed catalogue to {args.out}")
    print(f"Total packs catalogued: {len(catalogue['packs'])}")
    for p in catalogue["packs"]:
        print(f"  - {p['packId']} ({p['languageTag']}): {p['bytes']:,} bytes | SHA256: {p['sha256'][:16]}...")
    return 0


if __name__ == "__main__":
    sys.exit(main())

