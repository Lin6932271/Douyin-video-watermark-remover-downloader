"""Dependency-free reproducible APK build using JDK 17 + official SDK tools."""
import hashlib
import json
import os
import pathlib
import shutil
import subprocess
import sys
import tempfile
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
SDK = pathlib.Path(os.environ.get('ANDROID_SDK_ROOT', ROOT / '.tools/android-sdk'))
BT = SDK / 'build-tools/35.0.0'
ANDROID = SDK / 'platforms/android-35/android.jar'
JAVA_HOME = pathlib.Path(os.environ.get('JAVA_HOME_17', r'C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot'))
JAVA = JAVA_HOME / 'bin/java.exe'
JAVAC = JAVA_HOME / 'bin/javac.exe'
DIST = ROOT / 'dist'
KEYS = ROOT / '.build/signing'
DIST.mkdir(exist_ok=True)
KEYS.mkdir(parents=True, exist_ok=True)
STAGE = pathlib.Path(tempfile.mkdtemp(prefix='shiying-build-', dir=os.environ.get('TEMP')))
def run(args):
    args = [str(a) for a in args]
    print('RUN ' + pathlib.Path(args[0]).name + ' ' + ' '.join(args[1:3]), flush=True)
    subprocess.run(args, cwd=STAGE, check=True)

if not ANDROID.exists() or not BT.exists():
    raise SystemExit('Missing tools; run: python scripts/setup_tools.py')
shutil.copytree(ROOT / 'app/src/main', STAGE / 'main')
manifest = STAGE / 'main/AndroidManifest.xml'
manifest.write_text(manifest.read_text(encoding='utf-8').replace('<manifest ', '<manifest package="com.aojiao.shiying" ', 1), encoding='utf-8')
shutil.copy2(ANDROID, STAGE / 'android.jar')
ANDROID = STAGE / 'android.jar'
(STAGE / 'classes').mkdir()
(STAGE / 'generated').mkdir()
(STAGE / 'dex').mkdir()
run([BT / 'aapt2.exe', 'compile', '--dir', STAGE / 'main/res', '-o', STAGE / 'resources.zip'])
run([BT / 'aapt2.exe', 'link', '-I', ANDROID, '--manifest', STAGE / 'main/AndroidManifest.xml',
     '--java', STAGE / 'generated', '--version-code', '1', '--version-name', '1.0.0',
     '-A', STAGE / 'main/assets', '-o', STAGE / 'unsigned.apk', STAGE / 'resources.zip'])
sources = sorted((STAGE / 'main/java').rglob('*.java')) + sorted((STAGE / 'generated').rglob('*.java'))
args = ['-encoding', 'UTF-8', '-source', '8', '-target', '8', '-classpath', str(ANDROID), '-d', str(STAGE / 'classes')]
argfile = STAGE / 'javac.args'
argfile.write_text('\n'.join('"' + str(v).replace('\\', '/') + '"' for v in args + sources), encoding='utf-8')
run([JAVAC, '-J-Dfile.encoding=UTF-8', '@' + str(argfile)])
classes_jar = STAGE / 'classes.jar'
with zipfile.ZipFile(classes_jar, 'w') as z:
    for p in (STAGE / 'classes').rglob('*.class'):
        z.write(p, p.relative_to(STAGE / 'classes').as_posix())
run([JAVA, '-cp', BT / 'lib/d8.jar', 'com.android.tools.r8.D8', '--release', '--min-api', '29',
     '--lib', ANDROID, '--output', STAGE / 'dex', classes_jar])
with zipfile.ZipFile(STAGE / 'unsigned.apk', 'a', compression=zipfile.ZIP_DEFLATED) as z:
    for p in (STAGE / 'dex').glob('*.dex'): z.write(p, p.name)
run([BT / 'zipalign.exe', '-f', '-p', '4', STAGE / 'unsigned.apk', STAGE / 'aligned.apk'])
keystore = KEYS / 'shiying-release.jks'
password_file = KEYS / 'password.txt'
if not keystore.exists():
    import secrets
    password_file.write_text(secrets.token_urlsafe(32), encoding='utf-8')
    run([JAVA_HOME / 'bin/keytool.exe', '-genkeypair', '-keystore', keystore, '-storepass:file', password_file,
         '-keypass:file', password_file, '-alias', 'shiying', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000',
         '-dname', 'CN=Shiying Local Build,OU=Development,O=aojiao,L=Local,ST=Local,C=CN'])
apk = DIST / 'shiying-1.0.0.apk'
if apk.exists(): shutil.copy2(apk, DIST / 'shiying-1.0.0.apk.work')
run([JAVA, '-jar', BT / 'lib/apksigner.jar', 'sign', '--ks', keystore, '--ks-key-alias', 'shiying',
     '--ks-pass', 'file:' + str(password_file), '--out', apk, STAGE / 'aligned.apk'])
run([JAVA, '-jar', BT / 'lib/apksigner.jar', 'verify', '--verbose', '--print-certs', apk])
run([BT / 'zipalign.exe', '-c', '-p', '4', apk])
run([BT / 'aapt2.exe', 'dump', 'badging', apk])
with zipfile.ZipFile(apk) as z:
    assert z.testzip() is None
    assert 'classes.dex' in z.namelist() and 'assets/inspect-page.js' in z.namelist()
report = {'apk': str(apk), 'sha256': hashlib.sha256(apk.read_bytes()).hexdigest(), 'bytes': apk.stat().st_size,
          'package': 'com.aojiao.shiying', 'min_sdk': 29, 'target_sdk': 35,
          'signature_verified': True, 'zipalign_verified': True, 'zip_integrity_verified': True,
          'build_stage': str(STAGE), 'jdk': str(JAVA_HOME), 'device_installation_performed': False}
(DIST / 'build-report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
print('BUILD_OK ' + json.dumps(report, ensure_ascii=False), flush=True)
