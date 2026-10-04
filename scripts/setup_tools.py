"""Install official standalone Android build tools in a project-local cache."""
import hashlib
import json
import pathlib
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
TOOLS = ROOT / '.tools'
TOOLS.mkdir(exist_ok=True)
REPO = 'https://dl.google.com/android/repository/'
def get(url, dest):
    if dest.exists() and dest.stat().st_size:
        return
    print('Downloading ' + url, flush=True)
    partial = dest.with_suffix(dest.suffix + '.part')
    with urllib.request.urlopen(url, timeout=60) as src, partial.open('wb') as out:
        while chunk := src.read(1024 * 1024):
            out.write(chunk)
    partial.replace(dest)

xml_path = TOOLS / 'repository2-1.xml'
get(REPO + 'repository2-1.xml', xml_path)
xml = ET.parse(xml_path).getroot()
packages = {p.attrib['path']: p for p in xml.findall('remotePackage')}
sdk = TOOLS / 'android-sdk'
sdk.mkdir(exist_ok=True)
installed = {}
for name, relative in [('platforms;android-35', 'platforms/android-35'), ('build-tools;35.0.0', 'build-tools/35.0.0')]:
    package = packages[name]
    archives = package.find('archives').findall('archive')
    archive = next(a for a in archives if a.findtext('host-os') in (None, 'windows'))
    complete = archive.find('complete')
    url = REPO + complete.findtext('url')
    dest = TOOLS / url.rsplit('/', 1)[1]
    get(url, dest)
    expected = complete.findtext('checksum')
    actual = hashlib.sha1(dest.read_bytes()).hexdigest()
    if actual != expected:
        raise RuntimeError('Official archive SHA1 mismatch: ' + dest.name)
    target = sdk / relative
    marker = target / '.installed'
    if not marker.exists():
        target.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(dest) as z:
            roots = {n.split('/')[0] for n in z.namelist() if '/' in n}
            if len(roots) != 1:
                raise RuntimeError('Unexpected SDK archive layout')
            prefix = next(iter(roots)) + '/'
            for entry in z.infolist():
                name_in = entry.filename.removeprefix(prefix)
                if not name_in or entry.is_dir():
                    continue
                path = (target / name_in).resolve()
                if not path.is_relative_to(target.resolve()):
                    raise RuntimeError('Unsafe SDK archive path')
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(z.read(entry))
        marker.write_text(actual)
    installed[name] = {'url': url, 'sha1': actual, 'path': str(target)}
get('https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar', TOOLS / 'json-20240303.jar')
(TOOLS / 'tool-provenance.json').write_text(json.dumps(installed, indent=2), encoding='utf-8')
print('SDK_READY=' + str(sdk), flush=True)
