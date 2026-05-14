#!/usr/bin/env python3
"""
Change port numbers throughout the codebase.

Prompts for four port numbers and replaces all occurrences of the old
port patterns with the new values.
"""

import os
import re
import sys

# Determine project root (parent of scripts/initialisation)
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(os.path.dirname(SCRIPT_DIR))

EXCLUDED_DIRS = {
    '.git', 'target', 'node_modules', '.windsurf', 'dist', '.angular',
    '.mvn', '__pycache__', '.idea', '.vscode',
}

EXCLUDED_EXTENSIONS = {
    '.zip', '.jar', '.class', '.so', '.dylib', '.dll', '.exe',
    '.bin', '.o', '.a', '.png', '.jpg', '.jpeg', '.gif', '.ico',
    '.woff', '.woff2', '.ttf', '.eot', '.pdf', '.mp3', '.mp4',
}


def is_text_file(filepath):
    """Check whether a file is a text file by attempting UTF-8 decoding."""
    try:
        with open(filepath, 'rb') as f:
            chunk = f.read(8192)
            chunk.decode('utf-8')
        return True
    except (UnicodeDecodeError, OSError):
        return False


def replace_in_file(filepath, replacements):
    """
    Apply a list of regex replacements to a single file.

    :param filepath: path to the file
    :param replacements: list of (compiled_pattern, replacement_string)
    :return: True if the file was modified, False otherwise
    """
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    original = content
    for pattern, replacement in replacements:
        content = pattern.sub(replacement, content)

    if content != original:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        return True
    return False


def main():
    print("Enter the new port numbers:")
    web_port = input("Web port (e.g. 8085): ").strip()
    test_port = input("Test port (e.g. 10085): ").strip()
    angular_port = input("Angular port (e.g. 4205): ").strip()
    management_port = input("Management port (e.g. 9007): ").strip()

    # Validate inputs
    for name, port in [
        ("Web", web_port),
        ("Test", test_port),
        ("Angular", angular_port),
        ("Management", management_port),
    ]:
        if not port.isdigit():
            print(f"Error: {name} port '{port}' is not a valid number.", file=sys.stderr)
            sys.exit(1)
        p = int(port)
        if not (1 <= p <= 65535):
            print(f"Error: {name} port {p} is out of range (1-65535).", file=sys.stderr)
            sys.exit(1)

    replacements = [
        # Web port: 808x (literal x or digit), not preceded or followed by a digit
        (re.compile(r'(?<!\d)808[0-9x](?!\d)'), web_port),
        # Test port: 1008x (literal x or digit)
        (re.compile(r'(?<!\d)1008[0-9x](?!\d)'), test_port),
        # Angular port: 420x (literal x or digit)
        (re.compile(r'(?<!\d)420[0-9x](?!\d)'), angular_port),
        # Management port: 900x (literal x or digit)
        (re.compile(r'(?<!\d)900[0-9x](?!\d)'), management_port),
    ]

    changed_files = []
    self_path = os.path.abspath(__file__)

    for root, dirs, files in os.walk(PROJECT_ROOT):
        # Prune excluded directories
        dirs[:] = [d for d in dirs if d not in EXCLUDED_DIRS]

        for filename in files:
            filepath = os.path.join(root, filename)

            # Skip this script itself
            if os.path.abspath(filepath) == self_path:
                continue

            # Skip files with known binary extensions
            _, ext = os.path.splitext(filename)
            if ext.lower() in EXCLUDED_EXTENSIONS:
                continue

            if not is_text_file(filepath):
                continue

            try:
                if replace_in_file(filepath, replacements):
                    changed_files.append(filepath)
            except OSError as e:
                print(f"Warning: could not process {filepath}: {e}", file=sys.stderr)

    print(f"\nDone. Modified {len(changed_files)} file(s):")
    for f in changed_files:
        rel = os.path.relpath(f, PROJECT_ROOT)
        print(f"  {rel}")


if __name__ == '__main__':
    main()
