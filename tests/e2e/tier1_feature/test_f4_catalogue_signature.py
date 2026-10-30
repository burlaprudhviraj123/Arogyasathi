"""Tier 1 Feature Coverage: Catalogue Cryptographic Signature Verification.

Verifies:
- ECDSA NIST P-256 (SHA256withECDSA) keypair generation and signing
- Verification of signed catalogue JSON envelopes
- Rejection of tampered payloads (1-byte / 1-character changes)
- Rejection of signatures verified against an untrusted public key
- Rejection of corrupted / bit-flipped signatures
- Enforcement of algorithm requirement (SHA256withECDSA)
"""

from __future__ import annotations

import json
import unittest

from tests.e2e.common.crypto_verifier import (
    CatalogueSignatureError,
    create_signed_catalogue_envelope,
    generate_ecdsa_keypair,
    sign_catalogue_payload,
    verify_catalogue_envelope,
    verify_catalogue_signature,
)


class TestCatalogueSignatureFeature(unittest.TestCase):
    """Test suite verifying catalogue cryptographic signature verification."""

    def setUp(self) -> None:
        self.priv_key, self.pub_key_b64 = generate_ecdsa_keypair()
        self.sample_catalogue = {
            "schemaVersion": 1,
            "publishedAt": "2026-10-30T18:00:00Z",
            "minAppVersionCode": 1,
            "packs": [
                {
                    "packId": "arogyasathi-asr-te-in",
                    "languageTag": "te-IN",
                    "version": "1.0.0",
                    "downloadUrl": "https://assets.arogyasathi.org/voice-packs/te-IN/pack-1.0.0.zip",
                    "bytes": 135894120,
                    "sha256": "a22d91910dcc65859e4ab4e78360af7a794af7f5436c7e002d03e2bc5b548822",
                }
            ],
        }

    def test_valid_signed_catalogue_accepted(self) -> None:
        """Verify that a legitimately signed catalogue envelope passes verification."""
        envelope = create_signed_catalogue_envelope(self.priv_key, self.sample_catalogue)
        parsed = verify_catalogue_envelope(envelope, self.pub_key_b64)

        self.assertEqual(parsed["schemaVersion"], 1)
        self.assertEqual(len(parsed["packs"]), 1)
        self.assertEqual(parsed["packs"][0]["packId"], "arogyasathi-asr-te-in")

    def test_tampered_payload_rejected(self) -> None:
        """Verify that any modification to payload string causes signature verification failure."""
        envelope = create_signed_catalogue_envelope(self.priv_key, self.sample_catalogue)

        # Alter one character in payload (e.g. change version 1.0.0 to 1.0.1)
        tampered_payload = envelope["payload"].replace("1.0.0", "1.0.1")
        envelope["payload"] = tampered_payload

        with self.assertRaises(CatalogueSignatureError):
            verify_catalogue_envelope(envelope, self.pub_key_b64)

    def test_wrong_public_key_rejected(self) -> None:
        """Verify that signature verification fails if verified against a different public key."""
        envelope = create_signed_catalogue_envelope(self.priv_key, self.sample_catalogue)

        # Generate an attacker / different keypair
        _, rogue_pub_key_b64 = generate_ecdsa_keypair()

        with self.assertRaises(CatalogueSignatureError):
            verify_catalogue_envelope(envelope, rogue_pub_key_b64)

    def test_corrupted_signature_rejected(self) -> None:
        """Verify that tampering with the signature bytes causes rejection."""
        envelope = create_signed_catalogue_envelope(self.priv_key, self.sample_catalogue)

        # Tamper signature characters
        sig = list(envelope["signature"])
        sig[10] = "A" if sig[10] != "A" else "B"
        envelope["signature"] = "".join(sig)

        with self.assertRaises(CatalogueSignatureError):
            verify_catalogue_envelope(envelope, self.pub_key_b64)

    def test_unsupported_algorithm_rejected(self) -> None:
        """Verify that envelope specifying unsupported algorithm is rejected."""
        envelope = create_signed_catalogue_envelope(self.priv_key, self.sample_catalogue)
        envelope["algorithm"] = "SHA256withRSA"

        with self.assertRaises(CatalogueSignatureError):
            verify_catalogue_envelope(envelope, self.pub_key_b64)


if __name__ == "__main__":
    unittest.main()
