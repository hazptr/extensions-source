"""
versions.json on the repo branch: every extension package ever published, with the last versionCode it shipped
with and that build's content hash. Entries are never dropped: a removed package keeps its entry (marked with the
date it left the index), so it continues from there if it comes back.

    {"<packageName>": {"versionCode": 106042, "contentHash": "<sha256>", "name": "Example", "removed": "2026-11-28"}}

contentHash is missing where it isn't known (published before content hashes); removed only on removed packages.
"""

import json
from pathlib import Path


def load(path: Path) -> dict[str, dict]:
    with path.open(encoding="utf-8") as f:
        return json.load(f)


def dump(ledger: dict[str, dict], path: Path) -> None:
    """One package per line, so the repo branch's history shows each version change as one line."""
    lines = [
        f"  {json.dumps(package)}: {json.dumps(entry, sort_keys=True)}"
        for package, entry in sorted(ledger.items())
    ]
    path.write_text("{\n" + ",\n".join(lines) + "\n}\n", encoding="utf-8")


def lib_version_base(lib_version: str) -> int:
    """The first Android versionCode of a libVersion: "1.6" is 106000."""
    return int("".join(part.zfill(2) for part in lib_version.split("."))) * 1000


def next_version_code(ledger: dict[str, dict], package: str, lib_version: str) -> int:
    """Matches nextVersionCode() in gradle/build-logic (PublishedVersions.kt)."""
    base = lib_version_base(lib_version)
    published = ledger.get(package, {}).get("versionCode", -1)
    return max(base, published + 1)
