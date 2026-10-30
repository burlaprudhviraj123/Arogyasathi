"""Cryptographic Catalogue Signing & Signature Verification Engine.

Derived strictly from ORIGINAL_REQUEST.md (§R2) and PROJECT.md (§Interface Contracts: M2 ↔ M3 / M4).
Uses ECDSA NIST P-256 (secp256r1) with SHA-256 (SHA256withECDSA), DER encoding, and Base64 envelopes.
100% compatible with Android java.security.Signature and X509EncodedKeySpec.
"""

from __future__ import annotations

import base64
import json
from typing import Any, Dict, Tuple

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.asymmetric.utils import Prehashed


class CatalogueSignatureError(ValueError):
    """Raised when catalogue signature verification fails or envelope is invalid."""
    pass


def generate_ecdsa_keypair() -> Tuple[ec.EllipticCurvePrivateKey, str]:
    """Generate a NIST P-256 ECDSA keypair.

    Returns:
        (private_key, base64_x509_public_key)
    """
    private_key = ec.generate_private_key(ec.SECP256R1())
    public_key = private_key.public_key()
    der_public_key = public_key.public_bytes(
        encoding=serialization.Encoding.DER,
        format=serialization.PublicFormat.SubjectPublicKeyInfo,
    )
    b64_public_key = base64.b64encode(der_public_key).decode("ascii")
    return private_key, b64_public_key


def sign_catalogue_payload(private_key: ec.EllipticCurvePrivateKey, payload_str: str) -> str:
    """Sign raw JSON payload string using SHA256withECDSA.

    Returns:
        Base64-encoded ASN.1 DER signature.
    """
    payload_bytes = payload_str.encode("utf-8")
    der_signature = private_key.sign(payload_bytes, ec.ECDSA(hashes.SHA256()))
    return base64.b64encode(der_signature).decode("ascii")


def create_signed_catalogue_envelope(
    private_key: ec.EllipticCurvePrivateKey,
    catalogue_dict: Dict[str, Any],
    key_id: str = "arogyasathi-release-2026",
) -> Dict[str, Any]:
    """Create a signed catalogue envelope containing canonical payload and signature."""
    payload_str = json.dumps(catalogue_dict, separators=(",", ":"), sort_keys=True)
    sig_b64 = sign_catalogue_payload(private_key, payload_str)
    return {
        "payload": payload_str,
        "signature": sig_b64,
        "algorithm": "SHA256withECDSA",
        "keyId": key_id,
    }


def verify_catalogue_signature(
    public_key_b64: str,
    payload_str: str,
    signature_b64: str,
    algorithm: str = "SHA256withECDSA",
) -> bool:
    """Verify raw JSON payload string against signature using Base64 X.509 public key.

    Returns:
        True if valid, False if signature mismatch.

    Raises:
        CatalogueSignatureError on malformed key, signature format, or unsupported algorithm.
    """
    if algorithm != "SHA256withECDSA":
        raise CatalogueSignatureError(f"Unsupported algorithm '{algorithm}', expected 'SHA256withECDSA'")

    try:
        der_public_bytes = base64.b64decode(public_key_b64, validate=True)
    except Exception as e:
        raise CatalogueSignatureError(f"Failed to Base64 decode public key: {e}") from e

    try:
        public_key = serialization.load_der_public_key(der_public_bytes)
        if not isinstance(public_key, ec.EllipticCurvePublicKey):
            raise CatalogueSignatureError("Public key is not an EllipticCurvePublicKey")
        if not isinstance(public_key.curve, ec.SECP256R1):
            raise CatalogueSignatureError(f"Public key curve '{public_key.curve.name}' is not SECP256R1 / NIST P-256")
    except Exception as e:
        raise CatalogueSignatureError(f"Failed to load X.509 DER public key: {e}") from e

    try:
        der_signature = base64.b64decode(signature_b64, validate=True)
    except Exception as e:
        raise CatalogueSignatureError(f"Failed to Base64 decode signature: {e}") from e

    try:
        public_key.verify(der_signature, payload_str.encode("utf-8"), ec.ECDSA(hashes.SHA256()))
        return True
    except InvalidSignature:
        return False
    except Exception as e:
        raise CatalogueSignatureError(f"Error during signature verification: {e}") from e


def verify_catalogue_envelope(
    envelope: Dict[str, Any],
    trusted_public_key_b64: str,
) -> Dict[str, Any]:
    """Verify a signed catalogue envelope and return parsed catalogue dictionary.

    Raises:
        CatalogueSignatureError on missing fields, bad signature, or invalid JSON.
    """
    for required in ("payload", "signature", "algorithm"):
        if required not in envelope:
            raise CatalogueSignatureError(f"Missing mandatory envelope field: '{required}'")

    payload_str = envelope["payload"]
    signature_b64 = envelope["signature"]
    algorithm = envelope["algorithm"]

    if not verify_catalogue_signature(trusted_public_key_b64, payload_str, signature_b64, algorithm):
        raise CatalogueSignatureError("Signature verification rejected: tampered payload or invalid signature")

    try:
        return json.loads(payload_str)
    except Exception as e:
        raise CatalogueSignatureError(f"Failed to parse validated catalogue JSON payload: {e}") from e
