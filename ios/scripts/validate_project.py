"""Static verification only. This cannot replace xcodebuild or an iPhone test."""
from pathlib import Path
import argparse
import json
import plistlib
import struct
import xml.etree.ElementTree as ET
from openstep_parser import OpenStepDecoder
from tree_sitter import Language, Parser
import tree_sitter_swift

ROOT = Path(__file__).resolve().parents[1]

def validate():
    with (ROOT / 'Shiying.xcodeproj/project.pbxproj').open(encoding='utf-8') as handle:
        doc = OpenStepDecoder.ParseFromFile(handle)
    objects = doc['objects']
    assert doc['rootObject'] in objects
    project = objects[doc['rootObject']]
    target_id = project['targets'][0]
    assert objects[target_id]['isa'] == 'PBXNativeTarget'
    references = {'children', 'buildPhases', 'buildConfigurations', 'targets', 'files', 'dependencies'}
    single = {'fileRef', 'buildConfigurationList', 'mainGroup', 'productRefGroup', 'productReference'}
    for name, obj in objects.items():
        for key in references:
            for ref in obj.get(key, []):
                assert ref in objects, (name, key, ref)
        for key in single:
            if key in obj: assert obj[key] in objects, (name, key, obj[key])
    resolved = {}
    def group_walk(ref, parent):
        obj = objects[ref]
        if obj['isa'] == 'PBXGroup':
            base = parent / obj.get('path', '')
            for child in obj.get('children', []): group_walk(child, base)
        elif obj['isa'] == 'PBXFileReference' and obj.get('sourceTree') != 'BUILT_PRODUCTS_DIR':
            path = parent / obj['path']
            assert path.exists(), 'Missing resource: ' + str(path)
            resolved[ref] = path
    group_walk(project['mainGroup'], ROOT)
    compiled, bundled = [], []
    for phase in objects[target_id]['buildPhases']:
        obj = objects[phase]
        files = [resolved[objects[f]['fileRef']] for f in obj.get('files', [])]
        if obj['isa'] == 'PBXSourcesBuildPhase': compiled += files
        if obj['isa'] == 'PBXResourcesBuildPhase': bundled += files
    assert set(compiled) == set((ROOT / 'Shiying').glob('*.swift'))
    assert {'parser.js', 'inspect-page.js', 'PrivacyInfo.xcprivacy', 'Assets.xcassets'} <= {p.name for p in bundled}
    plist = plistlib.loads((ROOT / 'Shiying/Info.plist').read_bytes())
    assert plist['NSPhotoLibraryAddUsageDescription']
    assert 'NSAllowsArbitraryLoads' not in str(plist)
    privacy = plistlib.loads((ROOT / 'Shiying/Resources/PrivacyInfo.xcprivacy').read_bytes())
    assert privacy['NSPrivacyTracking'] is False
    for path in (ROOT / 'Shiying/Assets.xcassets').rglob('Contents.json'): json.loads(path.read_text())
    png = (ROOT / 'Shiying/Assets.xcassets/AppIcon.appiconset/AppIcon.png').read_bytes()
    assert png[:8] == b'\x89PNG\r\n\x1a\n'
    assert struct.unpack('>II', png[16:24]) == (1024, 1024) and png[25] == 2
    scheme = ET.parse(ROOT / 'Shiying.xcodeproj/xcshareddata/xcschemes/Shiying.xcscheme')
    for ref in scheme.iter('BuildableReference'): assert ref.attrib['BlueprintIdentifier'] == target_id
    parser = Parser(Language(tree_sitter_swift.language()))
    swift_report = []
    for source in compiled:
        tree = parser.parse(source.read_bytes())
        if tree.root_node.has_error:
            bad = []
            def visit(node):
                if node.is_error or node.is_missing: bad.append({'type': node.type, 'line': node.start_point.row + 1, 'column': node.start_point.column + 1})
                for child in node.children: visit(child)
            visit(tree.root_node)
            raise AssertionError({'source': source.name, 'syntax_errors': bad})
        swift_report.append(source.name)
    return {'project_objects': len(objects), 'swift_syntax_files': swift_report, 'bundled_resources': len(bundled),
            'plist_and_scheme': 'valid', 'scope': 'OpenStep graph, resources, Swift syntax tree; no Swift typecheck, Xcode build, signing or device test.'}

if __name__ == '__main__':
    ap = argparse.ArgumentParser()
    ap.add_argument('--report', type=Path)
    args = ap.parse_args()
    report = validate()
    if args.report: args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report, ensure_ascii=False))
