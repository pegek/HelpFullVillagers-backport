#!/usr/bin/env python3
"""
Helpful Villagers log filter — extract only relevant entries from Minecraft/FML logs.

Usage:
  python filter_log.py latest.log [output.txt]
  python filter_log.py stdout-logs.txt [output.txt]
  python filter_log.py *.log          (wildcards via shell)

Without output file, results go to stdout.
"""

import sys, re, pathlib

# --- Lines that always interest us ---
ALWAYS_INCLUDE = [
    r'\[HV\]',                          # our own tagged debug messages
    r'at com\.spege\.helpfulvillagers', # stack trace frames from our code
    r'Caused by:',                      # exception chain
    r'java\.lang\.',                    # NPE / ClassCast etc.
    r'helpfulvillagers',                # any mention of the mod
    r'\bException\b',                   # generic exception
]

# --- Lines with WARN/ERROR only if they are from relevant sources ---
WARN_ERROR_INCLUDE = [
    r'WARN|ERROR',                      # combined with any ALWAYS_INCLUDE
    r'Can\'t keep up',                  # server overload
]

# --- Lines that are pure noise (even if they match above) ---
EXCLUDE = [
    r'Forge Version Check',
    r'Paul Lamb',
    r'SoundSystem',
    r'OpenAL',
    r'LWJGL',
    r'log4j:',                          # XML wrapper lines in stdout-logs.txt
    r'\[STDOUT\]\s+<',                  # log4j XML tags
    r'Program Name\s*:',
    r'MoreInfo Link\s*:',
    r'^\s*$',                           # blank
]

always_re  = re.compile('|'.join(ALWAYS_INCLUDE),  re.IGNORECASE)
warn_re    = re.compile('|'.join(WARN_ERROR_INCLUDE), re.IGNORECASE)
exclude_re = re.compile('|'.join(EXCLUDE), re.IGNORECASE)


def should_include(line: str) -> bool:
    if exclude_re.search(line):
        return False
    if always_re.search(line):
        return True
    # WARN/ERROR only when accompanied by something interesting
    if warn_re.search(line):
        return True
    return False


def filter_file(path: pathlib.Path) -> list[str]:
    results = []
    try:
        with path.open('r', encoding='utf-8', errors='replace') as f:
            for line in f:
                line = line.rstrip('\n')
                # Strip [STDOUT] prefix from stdout-logs.txt
                clean = re.sub(r'^\[STDOUT\]\s*', '', line).strip()
                if should_include(clean):
                    results.append(clean)
    except FileNotFoundError:
        print(f"[filter] File not found: {path}", file=sys.stderr)
    return results


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)

    input_paths = [pathlib.Path(a) for a in sys.argv[1:-1]] if len(sys.argv) > 2 else [pathlib.Path(sys.argv[1])]
    output_path = pathlib.Path(sys.argv[-1]) if len(sys.argv) > 2 else None

    # If last arg ends in .log or .txt but only one arg given, it's the input
    if len(sys.argv) == 2:
        input_paths = [pathlib.Path(sys.argv[1])]
        output_path = None

    all_lines = []
    for p in input_paths:
        lines = filter_file(p)
        if lines:
            all_lines.append(f"\n=== {p.name} ({len(lines)} relevant lines) ===")
            all_lines.extend(lines)

    if not all_lines:
        all_lines = ['[filter] No relevant entries found — mod ran cleanly or produced no output.']

    text = '\n'.join(all_lines)
    if output_path:
        output_path.write_text(text, encoding='utf-8')
        print(f"[filter] Wrote {len(all_lines)} lines to {output_path}")
    else:
        print(text)


if __name__ == '__main__':
    main()
