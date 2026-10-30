"""Tier 2 Boundary & Corner Cases: Catalogue Cryptography Boundaries.

Verifies:
- Empty and zero-length payloads
- Truncated and corrupt ASN.1 DER signatures
- Invalid Base64 character sets
- Public key boundary formats (truncated DER, non-P-256 curves)
- JSON whitespace sensitivity in signature validation
"""

from __future__ import annotations

import base64
import json
import unittest

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec

from tests.e2e.common.crypto_verifier import (
    CatalogueSignatureError,
    create_signed_catalogue_envelope,
    generate_ecdsa_keypair,
    sign_catalogue_payload,
    verify_catalogue_envelope,
    verify_catalogue_signature,
)


class TestCatalogueCryptoBoundaries(unittest.TestCase):
    """Test suite for cryptographic boundary cases and malformed signatures."""

    def setUp(self) -> None:
        self.priv_key, self.pub_key_b64 = generate_ecdsa_keypair()
        self.sample_payload = '{"schemaVersion":1,"packs":[]}'
        self.valid_sig = sign_catalogue_payload(self.priv_key, self.sample_payload)

    def test_empty_payload_verification(self) -> None:
        """Verify that signature for empty payload validates, but does not match non-empty payload."""
        empty_payload = ""
        sig_empty = sign_catalogue_payload(self.priv_key, empty_payload)

        # Empty payload validates against its own signature
        self.assertTrue(verify_catalogue_signature(self.pub_key_b64, empty_payload, sig_empty))
        # Empty payload does not validate against non-empty signature
        self.assertFalse(verify_catalogue_signature(self.pub_key_b64, empty_payload, self.valid_sig))

    def test_truncated_signature_rejected(self) -> None:
        """Verify that truncated signature strings raise CatalogueSignatureError."""
        # Valid signature is ~70-72 bytes DER (~96-100 Base64 chars). Truncate to 10 chars.
        truncated_sig = self.valid_sig[:10]
        # Depending on base64 padding, either b64 decode or signature parsing fails
        with self.assertRaises(CatalogueSignatureError):
            verify_catalogue_signature(self.pub_key_b64, self.sample_payload, truncated_sig)

    def test_corrupted_asn1_der_signature_rejected(self) -> None:
        """Verify that valid Base64 containing invalid ASN.1 DER structure is rejected."""
        # 70 bytes of 0xFF is valid Base64, but invalid DER sequence
        bogus_bytes = b"\xff" * 70
        bogus_b64 = base64.b64encode(bogus_bytes).decode("ascii")

        self.assertFalse(verify_catalogue_signature(self.pub_key_b64, self.sample_payload, bogus_b64))
        envelope = {
            "payload": self.sample_payload,
            "signature": bogus_b64,
            "algorithm": "SHA256withECDSA",
        }
        with self.assertRaises(CatalogueSignatureError):
            verify_catalogue_envelope(envelope, self.pub_key_b64)

    def test_invalid_base64_signature_characters_rejected(self) -> None:
        """Verify that non-Base64 characters trigger CatalogueSignatureError."""
        invalid_b64 = self.valid_sig + "??!!$$"
        with self.assertRaises(CatalogueSignatureError):
            verify_catalogue_signature(self.pub_key_b64, self.sample_payload, invalid_b64)

    def test_wrong_elliptic_curve_public_key_rejected(self) -> None:
        """Verify that public keys using non-P-256 curves (e.g. SECP384R1) are rejected."""
        priv_384 = ec.generate_private_key(ec.SECP384R1())
        pub_384 = priv_384.public_key()
        der_384 = pub_384.public_bytes(
            encoding=serialization.Encoding.DER,
            format=serialization.PublicFormat.SubjectPublicKeyInfo,
        )
        b64_384 = base64.b64encode(der_384).decode("ascii")

        with self.assertRaises(CatalogueSignatureError) as ctx:
            verify_catalogue_signature(b64_384, self.sample_payload, self.valid_sig)
        self.assertIn("not SECP256R1", str(ctx.exception))

    def test_json_whitespace_mutation_invalidates_signature(self) -> None:
        """Verify that modifying whitespace inside JSON payload causes signature rejection."""
        # Exact canonical string signed
        envelope = create_signed_catalogue_envelope(
            self.priv_key,
            {"schemaVersion": 1, "publishedAt": "2026-10-30T18:00:00Z"},
        )
        raw_payload = envelope["payload"]

        # Insert space before colon (valid JSON, but altered string bytes)
        mutated_payload = raw_payload.replace('":1', '" : 1')
        envelope["payload"] = mutated_payload

        with self.assertRaises(CatalogueSignatureError):
            verify_catalogue_envelope(envelope, self.pub_key_b64)


if __name__ == "__main__":
    unittest.main()
