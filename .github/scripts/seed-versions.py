"""
Builds versions.json, the published version of every extension package that ever existed, from history.

Run from an extensions-source checkout with full history:
    python seed-versions.py <repo-branch checkout with full history> <output file>

Published packages come from every index.json on the repo branch (their versionCode is what users were
offered, with the content hash first shipped at it). Modules removed from the source tree before the repo branch's history begins come from the git
history of src/: their last build file gives the name and the version they shipped with.
"""

import json
import re
import subprocess
import sys
from pathlib import Path

import versions

EXTENSION_PREFIX = "eu.kanade.tachiyomi.extension"
HASH_REGEX = re.compile(r"^[0-9a-f]{64}$")


def git(cwd: Path | None, *args: str) -> str:
    return subprocess.run(
        ["git", *args], cwd=cwd, capture_output=True, text=True, check=True
    ).stdout


def git_show(cwd: Path | None, rev: str, path: str) -> str | None:
    result = subprocess.run(
        ["git", "show", f"{rev}:{path}"], cwd=cwd, capture_output=True, text=True
    )
    return result.stdout if result.returncode == 0 else None


def from_repo_branch(repo: Path) -> dict[str, dict]:
    commits = git(repo, "log", "--reverse", "--format=%H %cs", "HEAD", "--", "index.json").split("\n")
    ledger: dict[str, dict] = {}
    live: set[str] = set()
    for line in filter(None, commits):
        sha, date = line.split()
        index = json.loads(git_show(repo, sha, "index.json"))
        assets = json.loads(git_show(repo, sha, "release-assets.json") or "{}")
        current = set()
        for ext in index["extensionList"]["extensions"]:
            package = ext["packageName"]
            current.add(package)
            entry = ledger.setdefault(package, {"versionCode": 0})
            entry["name"] = ext["name"]
            entry.pop("removed", None)
            version_code = int(ext["versionCode"])
            if version_code <= entry["versionCode"]:
                continue
            entry["versionCode"] = version_code
            # The hash of the build first shipped at this versionCode: a later rebuild at the same code
            # reached new installs only, so the next build gets a new version. Content hashes only exist
            # since #20334; before that the values were file hashes.
            content_hash = assets.get(package)
            if isinstance(content_hash, str) and HASH_REGEX.match(content_hash):
                entry["contentHash"] = content_hash
            else:
                entry.pop("contentHash", None)
        for package in live - current:
            ledger[package]["removed"] = date
        live = current
    return ledger


def build_file_info(content: str, rev: str, theme_cache: dict) -> tuple[str | None, int, str]:
    """name, version within the lib version, lib version"""

    def find(pattern: str) -> str | None:
        match = re.search(pattern, content)
        return match.group(1) if match else None

    name = find(r"""extName\s*=\s*['"]([^'"]+)""") or find(r"""\bname\s*=\s*"([^"]+)""")
    version = int(
        find(r"\b(?:extVersionCode|overrideVersionCode|versionCode)\s*=\s*(\d+)") or 0
    )
    theme = find(r"""\b(?:themePkg|theme)\s*=\s*['"](\w+)""")
    if theme:
        key = (rev, theme)
        if key not in theme_cache:
            theme_file = git_show(None, rev, f"lib-multisrc/{theme}/build.gradle.kts") or ""
            match = re.search(r"baseVersionCode\s*=\s*(\d+)", theme_file)
            theme_cache[key] = int(match.group(1)) if match else 0
        version += theme_cache[key]
    lib_version = find(r"""libVersion\s*=\s*['"]([\d.]+)""") or "1.4"
    return name, version, lib_version


def from_source_history() -> dict[str, dict]:
    log = git(
        None, "log", "--no-renames", "--diff-filter=D", "--format=@%H %cs", "--name-only", "HEAD",
        "--", "src/*/*/build.gradle", "src/*/*/build.gradle.kts",
    )
    # The newest deletion of each module's build file, for modules gone from HEAD
    removed: dict[str, tuple[str, str, str]] = {}
    sha = date = ""
    for line in log.split("\n"):
        if line.startswith("@"):
            sha, date = line[1:].split()
        elif line:
            module_dir = str(Path(line).parent)
            if module_dir not in removed and not Path(module_dir).is_dir():
                removed[module_dir] = (sha, date, line)

    ledger: dict[str, dict] = {}
    theme_cache: dict = {}
    for module_dir, (sha, date, build_file) in removed.items():
        content = git_show(None, f"{sha}^", build_file)
        if content is None:
            continue
        _, lang, extension = module_dir.split("/")
        suffix = re.search(r"""pkgName\s*=\s*["']([^"']+)""", content)
        package = f"{EXTENSION_PREFIX}.{suffix.group(1) if suffix else f'{lang}.{extension}'}"
        name, version, lib_version = build_file_info(content, f"{sha}^", theme_cache)
        ledger[package] = {
            "versionCode": versions.lib_version_base(lib_version) + version,
            "name": name or extension,
            "removed": date,
        }
    return ledger


def main() -> None:
    _, repo, output = sys.argv
    ledger = from_source_history()
    for package, entry in from_repo_branch(Path(repo)).items():
        mined = ledger.get(package)
        if mined:
            entry["versionCode"] = max(entry["versionCode"], mined["versionCode"])
        ledger[package] = entry

    versions.dump(ledger, Path(output))

    removed = sum(1 for entry in ledger.values() if "removed" in entry)
    print(f"{len(ledger)} packages, {len(ledger) - removed} published now, {removed} removed")


if __name__ == "__main__":
    main()
