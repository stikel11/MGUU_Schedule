import zipfile
import re
import subprocess
import os

def run_dexdump(apk_path):
    print(f"\nAnalyzing {apk_path} classes...")
    try:
        # We can extract classes.dex and use a tool, but I can also just run strings or try to find a dexdump equivalent.
        # Wait, Python doesn't have a built-in dex parser easily available.
        # But we can look at the strings inside the dex files to approximate class names.
        pass
    except Exception as e:
        pass

def count_classes_via_strings(apk_path):
    z = zipfile.ZipFile(apk_path)
    dex_files = [f for f in z.namelist() if f.startswith('classes') and f.endswith('.dex')]

    app_classes = set()

    for dex in dex_files:
        data = z.read(dex)
        # simplistic string extraction
        # We look for "Lcom/mguuschedule/..."
        import re
        matches = re.findall(b'L(com/mguuschedule/[a-zA-Z0-9_/$]+);', data)
        for m in matches:
            app_classes.add(m.decode('utf-8').replace('/', '.'))

    return app_classes

debug_classes = count_classes_via_strings('app/build/outputs/apk/debug/app-debug.apk')
release_classes = count_classes_via_strings('app/build/outputs/apk/release/app-release.apk')

print(f"Total MGUU classes in Debug: {len(debug_classes)}")
print(f"Total MGUU classes in Release: {len(release_classes)}")

missing = debug_classes - release_classes
print(f"\nMissing classes in Release ({len(missing)}):")
# only print some or group them
from collections import defaultdict
grouped = defaultdict(list)
for c in missing:
    pkg = '.'.join(c.split('.')[:-1])
    grouped[pkg].append(c)

for pkg in sorted(grouped.keys()):
    print(f"  {pkg}: {len(grouped[pkg])} classes missing")
    if len(grouped[pkg]) < 20:
        for c in grouped[pkg]:
            print(f"    - {c}")
