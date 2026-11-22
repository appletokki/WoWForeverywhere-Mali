"""Prints how many launcher strings (fw_*) have no Korean translation yet. Used by the Mali release notes."""
import re
import sys
from pathlib import Path

res = Path(__file__).resolve().parent.parent / "app/src/main/res"
names = lambda p: set(re.findall(r'<string name="(fw_[^"]+)"', p.read_text(encoding="utf-8-sig")))
missing = sorted(names(res / "values/strings.xml") - names(res / "values-ko/strings.xml"))
print(len(missing))
if "-v" in sys.argv:
    print("\n".join(missing), file=sys.stderr)
