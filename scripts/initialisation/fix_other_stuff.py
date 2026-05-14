#!/usr/bin/env python3
"""
Fix remaining project-name references and clean up baseline-specific files.

1. Replace all occurrences of the literal string "abstracore" (case-sensitive)
   with the name of the project root folder.
2. Skip the file scripts/sync-base.sh.
3. In TODO.md, delete the line "# TODOs for Abstracore (to be deleted downstream)"
   and everything that follows it.
4. Delete the file copy_to_.git_hooks_pre-commit if it exists.
"""

import os
import sys

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(os.path.dirname(SCRIPT_DIR))
PROJECT_NAME = os.path.basename(PROJECT_ROOT)

EXCLUDED_DIRS = {
    '.git', 'target', 'node_modules', '.windsurf', 'dist', '.angular',
    '.mvn', '__pycache__', '.idea', '.vscode',
}

EXCLUDED_EXTENSIONS = {
    '.zip', '.jar', '.class', '.so', '.dylib', '.dll', '.exe',
    '.bin', '.o', '.a', '.png', '.jpg', '.jpeg', '.gif', '.ico',
    '.woff', '.woff2', '.ttf', '.eot', '.pdf', '.mp3', '.mp4',
}

OLD_NAME = "abstracore"
SKIP_FILE = os.path.join(PROJECT_ROOT, "scripts", "sync-base.sh")
TODO_FILE = os.path.join(PROJECT_ROOT, "TODO.md")
DELETE_FILE = os.path.join(PROJECT_ROOT, "copy_to_.git_hooks_pre-commit")


def is_text_file(filepath):
    """Check whether a file is a text file by attempting UTF-8 decoding."""
    try:
        with open(filepath, 'rb') as f:
            chunk = f.read(8192)
            chunk.decode('utf-8')
        return True
    except (UnicodeDecodeError, OSError):
        return False


def replace_in_file(filepath, old, new):
    """
    Replace all occurrences of *old* with *new* in a single file.

    :return: True if the file was modified, False otherwise
    """
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    if old not in content:
        return False

    new_content = content.replace(old, new)
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(new_content)
    return True


def main():
    print(f"Replacing '{OLD_NAME}' with '{PROJECT_NAME}' ...")

    changed_files = []
    self_path = os.path.abspath(__file__)

    for root, dirs, files in os.walk(PROJECT_ROOT):
        dirs[:] = [d for d in dirs if d not in EXCLUDED_DIRS]

        for filename in files:
            filepath = os.path.join(root, filename)
            abs_path = os.path.abspath(filepath)

            if abs_path == self_path:
                continue
            if abs_path == os.path.abspath(SKIP_FILE):
                continue

            _, ext = os.path.splitext(filename)
            if ext.lower() in EXCLUDED_EXTENSIONS:
                continue

            if not is_text_file(filepath):
                continue

            try:
                if replace_in_file(filepath, OLD_NAME, PROJECT_NAME):
                    changed_files.append(filepath)
            except OSError as e:
                print(f"Warning: could not process {filepath}: {e}", file=sys.stderr)

    print(f"  Modified {len(changed_files)} file(s).")

    # --- Trim TODO.md ---
    print("Trimming TODO.md ...")
    marker = "# TODOs for Abstracore (to be deleted downstream)"
    if os.path.exists(TODO_FILE):
        with open(TODO_FILE, 'r', encoding='utf-8') as f:
            lines = f.readlines()

        cut_index = None
        for i, line in enumerate(lines):
            if line.strip() == marker:
                cut_index = i
                break

        if cut_index is not None:
            with open(TODO_FILE, 'w', encoding='utf-8') as f:
                f.writelines(lines[:cut_index])
            print(f"  Removed line {cut_index + 1} and everything after it.")
        else:
            print(f"  Marker line not found; no changes made.")
    else:
        print(f"  {TODO_FILE} not found.")

    # --- Delete copy_to_.git_hooks_pre-commit ---
    print(f"Deleting {DELETE_FILE} ...")
    if os.path.exists(DELETE_FILE):
        os.remove(DELETE_FILE)
        print("  Deleted.")
    else:
        print("  File does not exist; nothing to do.")

    print("\nDone.")


if __name__ == '__main__':
    main()
