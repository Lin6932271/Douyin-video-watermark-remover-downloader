import hashlib
import json
import pathlib
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
paths = []
for folder in ('app', 'scripts', 'tests', 'docs', 'ios'):
    for p in (ROOT / folder).rglob('*'):
        if p.is_file() and not any(part in ('__pycache__', '.build', 'build', 'dist', '.tools', 'xcuserdata', '.venv') for part in p.parts) and '.work' not in p.name and p.suffix not in ('.pyc', '.zip', '.jks', '.p12', '.mobileprovision'):
            paths.append(p)
for name in ('README.md', 'CHANGELOG.md', 'build.ps1', 'build.gradle', 'settings.gradle', '.gitignore'):
    paths.append(ROOT / name)
manifest = {p.relative_to(ROOT).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in paths}
out = ROOT / 'dist/shiying-1.2.0-source.zip'
if out.exists():
    import shutil
    shutil.copy2(out, pathlib.Path(str(out) + '.work'))
with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as z:
    for p in sorted(paths): z.write(p, 'Shiying/' + p.relative_to(ROOT).as_posix())
    z.writestr('Shiying/source-manifest.json', json.dumps(manifest, ensure_ascii=False, indent=2))
with zipfile.ZipFile(out) as z:
    assert z.testzip() is None
    assert all('.tools/' not in n and '.build/' not in n and '.l-skill/' not in n and 'SKILL.md' not in n for n in z.namelist())
report = {'path': str(out), 'bytes': out.stat().st_size, 'sha256': hashlib.sha256(out.read_bytes()).hexdigest(),
          'source_files': len(paths), 'zip_integrity_verified': True, 'signing_key_included': False}
(ROOT / 'dist/source-report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps(report, ensure_ascii=False))
