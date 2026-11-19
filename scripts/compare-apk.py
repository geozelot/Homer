#!/usr/bin/env python3
"""Check that a signed release APK is an unsigned build of the same source, plus a signature.

This is the check F-Droid makes before it ships Homer's own signed APK: it builds the release commit
itself, unsigned, and the two must then be identical byte for byte once the signature is set aside.
Run it before tagging a release for F-Droid (see docs/F-DROID.md):

    python3 scripts/compare-apk.py homer-2.2.3.apk app/build/outputs/apk/release/app-release-unsigned.apk

Homer is signed with APK Signature Scheme v2 only (minSdk 26 needs no v1 JAR signature), so the
signature is one block between the zip entries and the central directory. Taking it out, and moving
the central directory's offset back by its size, gives the unsigned APK. On a mismatch the script
lists the zip entries that differ, which is where to start looking.
"""
import struct
import sys
import zipfile

MAGIC = b"APK Sig Block 42"


def strip_signing_block(data: bytes) -> bytes:
    end = data.rfind(b"PK\x05\x06")
    if end < 0:
        sys.exit("not a zip file: no end of central directory")
    cd_offset = struct.unpack_from("<I", data, end + 16)[0]
    if data[cd_offset - 16:cd_offset] != MAGIC:
        return data  # no v2+ signature to take out
    size = struct.unpack_from("<Q", data, cd_offset - 24)[0]
    start = cd_offset - (size + 8)
    out = bytearray(data[:start] + data[cd_offset:])
    struct.pack_into("<I", out, end - (cd_offset - start) + 16, start)
    return bytes(out)


def entries(path):
    with zipfile.ZipFile(path) as z:
        infos = z.infolist()
    return {i.filename: (i.CRC, i.file_size, i.compress_type, i.header_offset) for i in infos}, [
        i.filename for i in infos
    ]


def main(signed_path: str, unsigned_path: str) -> int:
    with open(signed_path, "rb") as f:
        signed = f.read()
    with open(unsigned_path, "rb") as f:
        unsigned = f.read()
    stripped = strip_signing_block(signed)
    print(f"signed {len(signed)} B, without its signature {len(stripped)} B, unsigned {len(unsigned)} B")
    if stripped == unsigned:
        print("IDENTICAL: the unsigned build is the signed APK, byte for byte, minus the signature.")
        return 0

    a, order_a = entries(signed_path)
    b, order_b = entries(unsigned_path)
    shared = set(a) & set(b)
    content = sorted(n for n in shared if a[n][:3] != b[n][:3])
    moved = sorted(n for n in shared if a[n][:3] == b[n][:3] and a[n][3] != b[n][3])
    print("DIFFERENT")
    print(f"  only in the signed APK:   {sorted(set(a) - set(b))[:20]}")
    print(f"  only in the unsigned one: {sorted(set(b) - set(a))[:20]}")
    print(f"  different content ({len(content)}): {content[:40]}")
    print(f"  same content, different place ({len(moved)}): {moved[:10]}")
    print(f"  same entry order: {order_a == order_b}")
    return 1


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    sys.exit(main(sys.argv[1], sys.argv[2]))
