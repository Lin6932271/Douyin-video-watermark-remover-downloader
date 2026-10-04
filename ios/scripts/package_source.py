"""Package this project only; never includes tools, credentials or raw sessions."""
from pathlib import Path
import hashlib
import json
import zipfile

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT.parent / 'dist'
PREFIX = 'shiying-ios-1.0.0'

def main():
    DEST.mkdir(exist_ok=True)
    archive = DEST / (PREFIX + '-source.zip')
    if archive.exists():
        backup = archive.with_name(archive.name + '.work')
        if backup.exists():
            raise FileExistsError('Existing archive and backup: select a new version before packaging again.')
        backup.write_bytes(archive.read_bytes())
    files = []
    for path in sorted(ROOT.rglob('*')):
        if not path.is_file(): continue
        relative = path.relative_to(ROOT)
        if any(part in {'build', '.venv', '__pycache__', 'xcuserdata'} for part in relative.parts): continue
        if '.work' in path.name or path.suffix in {'.ipa', '.p12', '.mobileprovision', '.xcuserstate'}: continue
        files.append((path, relative.as_posix()))
    hashes = {name: hashlib.sha256(path.read_bytes()).hexdigest() for path, name in files}
    manifest = {'version': '1.0.0', 'platform': 'iOS', 'files': hashes, 'compiled': False, 'signed': False, 'device_tested': False}
    with zipfile.ZipFile(archive, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as package:
        for path, name in files:
            info = zipfile.ZipInfo(PREFIX + '/' + name, date_time=(2020, 1, 1, 0, 0, 0))
            info.create_system = 3
            info.external_attr = ((0o100755 if path.suffix == '.sh' else 0o100644) << 16)
            info.compress_type = zipfile.ZIP_DEFLATED
            package.writestr(info, path.read_bytes())
        package.writestr(PREFIX + '/SOURCE-MANIFEST.json', json.dumps(manifest, ensure_ascii=False, indent=2) + '\n')
    with zipfile.ZipFile(archive) as package:
        assert package.testzip() is None
        assert len(package.namelist()) == len(files) + 1
        for name, digest in hashes.items():
            assert hashlib.sha256(package.read(PREFIX + '/' + name)).hexdigest() == digest
    report = {'archive': str(archive.resolve()), 'bytes': archive.stat().st_size,
              'sha256': hashlib.sha256(archive.read_bytes()).hexdigest(), 'source_files': len(files),
              'zip_integrity': 'pass', 'manifest_hashes': 'pass', 'compiled': False, 'signed': False}
    report_path = DEST / 'shiying-ios-1.0.0-source-report.json'
    if report_path.exists(): report_path.with_suffix('.json.work').write_bytes(report_path.read_bytes())
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report, ensure_ascii=False))

if __name__ == '__main__': main()
