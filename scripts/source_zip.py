from pathlib import Path
import hashlib
import zipfile

root = Path(__file__).resolve().parent.parent
excluded_dirs = {'.gradle', '.kotlin', '.build-tools', '.idea', 'build', 'node_modules', '.git', 'dist', '__pycache__'}
excluded_names = {'local.properties', 'keystore.properties'}
excluded_suffixes = {'.log', '.apk', '.aab', '.jks', '.keystore', '.hprof', '.iml', '.pyc'}
paths = []
for path in root.rglob('*'):
    relative = path.relative_to(root)
    if any(part in excluded_dirs for part in relative.parts):
        continue
    if path.is_symlink():
        raise RuntimeError(f'Symlink is not permitted: {relative}')
    if not path.is_file() or path.name in excluded_names or path.suffix in excluded_suffixes:
        continue
    paths.append(path)

required = ['gradlew', 'gradlew.bat', 'gradle/wrapper/gradle-wrapper.jar', '.github/workflows/build.yml',
            'app/src/main/assets/sana/snapshot.js', 'app/src/main/assets/ocr/fas.traineddata', 'tests/web/package-lock.json']
names = {path.relative_to(root).as_posix() for path in paths}
assert all(name in names for name in required), 'Required source file missing'
output = root / 'dist' / 'LawyerUp_Source_2.0.0.zip'
output.parent.mkdir(exist_ok=True)
manifest = []
with zipfile.ZipFile(output, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
    for path in sorted(paths):
        name = path.relative_to(root).as_posix()
        content = path.read_bytes()
        if name == 'gradlew':
            content = content.replace(b'\r\n', b'\n')
        info = zipfile.ZipInfo(name, (2026, 10, 20, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        info.create_system = 3
        info.external_attr = (0o100755 if name.endswith('.sh') or name == 'gradlew' else 0o100644) << 16
        archive.writestr(info, content)
        manifest.append(f'{hashlib.sha256(content).hexdigest()}  {name}')
    archive.writestr('SOURCE-SHA256SUMS', '\n'.join(manifest) + '\n')

with zipfile.ZipFile(output) as archive:
    assert archive.testzip() is None
    assert not any(name.startswith('/') or '..' in Path(name).parts for name in archive.namelist())
print(f'{output.name}: {len(paths)} files, {output.stat().st_size} bytes')
print('SHA256', hashlib.sha256(output.read_bytes()).hexdigest())
