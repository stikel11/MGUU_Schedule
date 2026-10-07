import zipfile

def analyze(path):
    try:
        z = zipfile.ZipFile(path)
    except Exception as e:
        print(f"Error opening {path}: {e}")
        return

    sums = {'classes.dex': 0, 'res': 0, 'lib': 0, 'META-INF': 0, 'assets': 0}
    others = {}

    classes_dex_count = 0

    for f in z.infolist():
        name = f.filename
        size = f.file_size
        matched = False

        if name.startswith('classes') and name.endswith('.dex'):
            sums['classes.dex'] += size
            classes_dex_count += 1
            matched = True
        else:
            for k in ['res', 'lib', 'META-INF', 'assets']:
                if name.startswith(k + '/'):
                    sums[k] += size
                    matched = True
                    break

        if not matched:
            others[name] = others.get(name, 0) + size

    print(f'\n{path}:')
    print(f'  Total classes*.dex files: {classes_dex_count}')
    for k, v in sums.items():
        print(f'  {k}: {v / 1024 / 1024:.2f} MB')
    for k, v in others.items():
        if v > 0:
            print(f'  {k}: {v / 1024 / 1024:.2f} MB')

analyze('app/build/outputs/apk/debug/app-debug.apk')
analyze('app/build/outputs/apk/release/app-release.apk')
