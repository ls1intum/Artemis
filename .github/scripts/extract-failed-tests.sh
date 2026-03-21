#!/usr/bin/env bash
# Parse JUnit XML for failed test case names and durations.
# Usage: extract-failed-tests.sh <results-xml-path>

XML="$1"

if [ ! -f "$XML" ]; then
  echo "failures=" >> "$GITHUB_OUTPUT"
  exit 0
fi

FAILURES=$(python3 -c "
import xml.etree.ElementTree as ET, sys
try:
    tree = ET.parse(sys.argv[1])
    lines = []
    for tc in tree.iter('testcase'):
        if tc.find('failure') is not None:
            name = tc.get('name', 'Unknown')
            time_s = float(tc.get('time', 0))
            m, s = divmod(int(time_s), 60)
            dur = f'{m}m {s}s' if m else f'{s}s'
            lines.append(f'{name} ({dur})')
    print('\n'.join(lines))
except Exception as e:
    print(f'XML parse error: {e}', file=sys.stderr)
" "$XML" || true)

EOF=$(dd if=/dev/urandom bs=15 count=1 status=none | base64)
echo "failures<<$EOF" >> "$GITHUB_OUTPUT"
echo "$FAILURES" >> "$GITHUB_OUTPUT"
echo "$EOF" >> "$GITHUB_OUTPUT"
