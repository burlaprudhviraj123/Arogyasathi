"""Zip Archive Security Sandbox & Safe Extraction Engine.

Implements the exact security sandbox requirements specified in ORIGINAL_REQUEST.md (§R1, R3):
- ZipSlip path traversal rejection (`..`)
- Absolute path rejection
- Symlink rejection (POSIX mode 0120000)
- 500 MB uncompressed size ceiling
- Undeclared / forbidden executable files rejection
"""

from __future__ import annotations

import os
import shutil
import zipfile
from pathlib import Path
from typing import Dict, List, Optional, Set

from .artifacts import MAX_UNCOMPRESSED_ARCHIVE_BYTES


class ZipSecurityError(Exception):
    """Base exception for zip security sandbox violations."""
    pass


class ZipSlipError(ZipSecurityError):
    """Raised when an entry attempts directory traversal."""
    pass


class ZipAbsolutePathError(ZipSecurityError):
    """Raised when an entry contains an absolute path."""
    pass


class ZipSymlinkError(ZipSecurityError):
    """Raised when an archive contains a symbolic link entry."""
    pass


class ZipSizeExceededError(ZipSecurityError):
    """Raised when uncompressed archive content exceeds 500 MB limit."""
    pass


class ZipUndeclaredFileError(ZipSecurityError):
    """Raised when an archive contains files not declared in manifest or forbidden executables."""
    pass


FORBIDDEN_EXTENSIONS = {".so", ".sh", ".exe", ".bin", ".apk", ".dex", ".elf", ".bat", ".cmd"}


def is_symlink(info: zipfile.ZipInfo) -> bool:
    """Check if ZipInfo corresponds to a Unix symbolic link (mode 0120000)."""
    mode = (info.external_attr >> 16) & 0o170000
    return mode == 0o120000


def extract_zip_safely(
    zip_path: Path,
    staging_dir: Path,
    declared_files: Optional[Set[str]] = None,
    max_uncompressed_bytes: int = MAX_UNCOMPRESSED_ARCHIVE_BYTES,
) -> List[Path]:
    """Extract a zip archive into staging_dir under strict sandbox isolation.

    Args:
        zip_path: Path to the .zip archive.
        staging_dir: Directory where files should be safely extracted.
        declared_files: Set of relative paths declared in pack.json (if None, only basic sanity).
        max_uncompressed_bytes: Maximum allowed cumulative uncompressed bytes (default 500 MB).

    Returns:
        List of extracted Path objects.

    Raises:
        ZipSlipError: If path traversal is attempted.
        ZipAbsolutePathError: If entry contains absolute path.
        ZipSymlinkError: If entry is a symlink.
        ZipSizeExceededError: If uncompressed size exceeds limit.
        ZipUndeclaredFileError: If entry is undeclared or has forbidden executable extension.
    """
    staging_canonical = staging_dir.resolve()
    staging_canonical.mkdir(parents=True, exist_ok=True)

    extracted_files: List[Path] = []
    total_uncompressed_bytes = 0

    try:
        with zipfile.ZipFile(zip_path, "r") as archive:
            # 1. Pre-scan all ZipInfo entries before any extraction
            for info in archive.infolist():
                name = info.filename

                # Absolute path check
                if name.startswith("/") or name.startswith("\\"):
                    raise ZipAbsolutePathError(f"Archive entry '{name}' has absolute path prefix")
                if len(name) > 2 and name[1] == ":" and name[2] in ("/\\"):
                    raise ZipAbsolutePathError(f"Archive entry '{name}' contains drive letter absolute path")

                # Directory traversal check
                # Check raw tokens and normalized canonical resolution
                parts = Path(name).parts
                if ".." in parts:
                    raise ZipSlipError(f"Archive entry '{name}' contains '..' path traversal sequence")

                # Symlink check
                if is_symlink(info):
                    raise ZipSymlinkError(f"Archive entry '{name}' is a symbolic link (mode 0120000)")

                # Forbidden extensions
                suffix = Path(name).suffix.lower()
                if suffix in FORBIDDEN_EXTENSIONS:
                    raise ZipUndeclaredFileError(f"Archive entry '{name}' has forbidden executable extension '{suffix}'")

                # Undeclared files check (if declared_files supplied)
                if declared_files is not None:
                    # 'pack.json' is always permitted as manifest
                    allowed = declared_files | {"pack.json"}
                    if name not in allowed:
                        raise ZipUndeclaredFileError(
                            f"Archive entry '{name}' is not declared in manifest files: {sorted(allowed)}"
                        )

            # 2. Extract with cumulative byte counter
            for info in archive.infolist():
                name = info.filename
                dest_path = (staging_canonical / name).resolve()

                # Second-order ZipSlip defense after resolution
                try:
                    dest_path.relative_to(staging_canonical)
                except ValueError:
                    raise ZipSlipError(f"Archive entry '{name}' resolves outside staging dir: {dest_path}")

                # Accumulate size
                total_uncompressed_bytes += info.file_size
                if total_uncompressed_bytes > max_uncompressed_bytes:
                    raise ZipSizeExceededError(
                        f"Uncompressed archive size {total_uncompressed_bytes} bytes exceeds ceiling {max_uncompressed_bytes} bytes"
                    )

                # Ensure parent directories exist
                dest_path.parent.mkdir(parents=True, exist_ok=True)

                # Extract chunk by chunk to prevent zip bombs with inflated headers
                with archive.open(info) as src, dest_path.open("wb") as dst:
                    bytes_written = 0
                    while chunk := src.read(65536):
                        bytes_written += len(chunk)
                        if total_uncompressed_bytes + (bytes_written - info.file_size) > max_uncompressed_bytes:
                            raise ZipSizeExceededError("Decompressed stream exceeded size limit")
                        dst.write(chunk)

                extracted_files.append(dest_path)

        return extracted_files

    except Exception:
        # Security: Clean up staging directory on any failure
        if staging_canonical.exists():
            shutil.rmtree(staging_canonical, ignore_errors=True)
            staging_canonical.mkdir(parents=True, exist_ok=True)
        raise
