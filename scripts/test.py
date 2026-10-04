import pathlib,os,subprocess,json
ROOT=pathlib.Path(__file__).resolve().parents[1]
JDK=pathlib.Path(os.environ.get('JAVA_HOME_17',r'C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot'))
OUTPUT=ROOT/'.build/test-classes'
OUTPUT.mkdir(parents=True,exist_ok=True)
JSON=ROOT/'.tools/json-20240303.jar'
NAMES=['VideoInfo','LinkTools','PageParser','HttpTransport','DouyinResolver']
sources=[ROOT/'app/src/main/java/com/aojiao/shiying'/f'{name}.java' for name in NAMES]+list((ROOT/'tests').glob('*.java'))
subprocess.run([str(JDK/'bin/javac.exe'),'-encoding','UTF-8','-source','8','-target','8','-cp',str(JSON),'-d',str(OUTPUT),*[str(p) for p in sources]],check=True)
extra=[]
if (ROOT/'evidence/browser-api-0.json').exists(): extra=[str(ROOT/'evidence/browser-api-0.json'), str(ROOT/'evidence/browser-snapshot.json')]
result=subprocess.run([str(JDK/'bin/java.exe'),'-Dfile.encoding=UTF-8','-cp',str(OUTPUT)+os.pathsep+str(JSON),'com.aojiao.shiying.ParserTests',*extra],capture_output=True,text=True,encoding='utf-8',check=True)
print(result.stdout)
(ROOT/'evidence').mkdir(exist_ok=True)
(ROOT/'evidence/parser-tests.txt').write_text(result.stdout,encoding='utf-8')
