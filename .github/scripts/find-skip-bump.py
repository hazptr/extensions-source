"""
Prints the package suffixes (JSON list) whose version bump this run skips: those changed by at least one commit
since the base, where every such commit was merged from a pull request labelled SKIP_LABEL. publish-repo.py keeps
their published apk and version.
"""

import importlib
import json
import os
import subprocess
import sys

from github_utils import run_gh

matrices = importlib.import_module("generate-build-matrices")

SKIP_LABEL = "Skip version bump"
EMPTY_TREE = "4b825dc642cb6eb9a060e54bf8d69288fbee4904"


def is_skipped(repo: str, sha: str) -> bool:
    pulls = json.loads(run_gh("api", f"repos/{repo}/commits/{sha}/pulls"))
    return any(
        pull["merged_at"] and any(label["name"] == SKIP_LABEL for label in pull["labels"])
        for pull in pulls
    )


def changed_suffixes(sha: str) -> set[str]:
    """package suffixes a commit rebuilds; for a merge commit, against its first parent"""
    modules, _, _, _ = matrices.get_module_list(f"{sha}^", sha)
    suffixes = set()
    for module in modules:
        match = matrices.MODULE_REGEX.match(module)
        suffixes.add(matrices.resolve_module_suffix(f"{sha}^", match.group("lang"), match.group("extension")))
    return suffixes


def main() -> None:
    _, base = sys.argv
    if base == EMPTY_TREE:
        print("[]")
        return

    commits = subprocess.run(
        ["git", "rev-list", "--first-parent", f"{base}..HEAD"], capture_output=True, text=True, check=True
    ).stdout.split()
    repo = os.environ["GITHUB_REPOSITORY"]
    skipped: set[str] = set()
    bumped: set[str] = set()
    for sha in commits:
        (skipped if is_skipped(repo, sha) else bumped).update(changed_suffixes(sha))
    print(json.dumps(sorted(skipped - bumped)))


if __name__ == "__main__":
    main()
