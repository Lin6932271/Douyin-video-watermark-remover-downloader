"""Reproducibly generate the checked-in Xcode project; no third-party packages."""
from pathlib import Path
import hashlib
import json
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1]
objects = {}

def ident(name):
    return hashlib.sha256(('shiying-ios:' + name).encode()).hexdigest()[:24].upper()

def add(object_name, **fields):
    key = ident(object_name)
    if key in objects:
        raise ValueError('Duplicate project object: ' + object_name)
    objects[key] = fields
    return key

def encode(value, depth=0):
    indent = '\t' * depth
    if isinstance(value, dict):
        return '{\n' + ''.join('\t' * (depth + 1) + json.dumps(str(k)) + ' = ' + encode(v, depth + 1) + ';\n' for k, v in value.items()) + indent + '}'
    if isinstance(value, list):
        return '(\n' + ''.join('\t' * (depth + 1) + encode(v, depth + 1) + ',\n' for v in value) + indent + ')'
    if isinstance(value, int):
        return str(value)
    return json.dumps(str(value), ensure_ascii=False)

def generate():
    sources = sorted((ROOT / 'Shiying').glob('*.swift'))
    source_refs, source_builds = [], []
    for path in sources:
        ref = add('file:' + path.name, isa='PBXFileReference', lastKnownFileType='sourcecode.swift', path=path.name, sourceTree='<group>')
        source_refs.append(ref)
        source_builds.append(add('build:' + path.name, isa='PBXBuildFile', fileRef=ref))
    resource_refs, resource_builds = [], []
    for name, kind in [('parser.js', 'sourcecode.javascript'), ('inspect-page.js', 'sourcecode.javascript'), ('PrivacyInfo.xcprivacy', 'text.xml')]:
        ref = add('file:' + name, isa='PBXFileReference', lastKnownFileType=kind, path=name, sourceTree='<group>')
        resource_refs.append(ref)
        resource_builds.append(add('build:' + name, isa='PBXBuildFile', fileRef=ref))
    resource_group = add('resources', isa='PBXGroup', children=resource_refs, path='Resources', sourceTree='<group>')
    plist = add('plist', isa='PBXFileReference', lastKnownFileType='text.plist.xml', path='Info.plist', sourceTree='<group>')
    assets = add('assets', isa='PBXFileReference', lastKnownFileType='folder.assetcatalog', path='Assets.xcassets', sourceTree='<group>')
    resource_builds.append(add('build:assets', isa='PBXBuildFile', fileRef=assets))
    app_group = add('app-group', isa='PBXGroup', children=source_refs + [resource_group, plist, assets], path='Shiying', sourceTree='<group>')
    product = add('product', isa='PBXFileReference', explicitFileType='wrapper.application', includeInIndex=0, path='Shiying.app', sourceTree='BUILT_PRODUCTS_DIR')
    product_group = add('products', isa='PBXGroup', children=[product], name='Products', sourceTree='<group>')
    root_group = add('root-group', isa='PBXGroup', children=[app_group, product_group], sourceTree='<group>')
    phases = [add('sources-phase', isa='PBXSourcesBuildPhase', buildActionMask=2147483647, files=source_builds, runOnlyForDeploymentPostprocessing=0),
              add('frameworks-phase', isa='PBXFrameworksBuildPhase', buildActionMask=2147483647, files=[], runOnlyForDeploymentPostprocessing=0),
              add('resources-phase', isa='PBXResourcesBuildPhase', buildActionMask=2147483647, files=resource_builds, runOnlyForDeploymentPostprocessing=0)]
    project_configs, target_configs = [], []
    for name in ['Debug', 'Release']:
        project_configs.append(add('project:' + name, isa='XCBuildConfiguration', name=name, buildSettings={
            'CLANG_ENABLE_MODULES': 'YES', 'CLANG_ENABLE_OBJC_ARC': 'YES', 'SDKROOT': 'iphoneos',
            'IPHONEOS_DEPLOYMENT_TARGET': '16.0', 'ENABLE_USER_SCRIPT_SANDBOXING': 'YES',
            'DEBUG_INFORMATION_FORMAT': 'dwarf' if name == 'Debug' else 'dwarf-with-dsym'}))
        target_configs.append(add('target:' + name, isa='XCBuildConfiguration', name=name, buildSettings={
            'ASSETCATALOG_COMPILER_APPICON_NAME': 'AppIcon', 'CODE_SIGN_STYLE': 'Automatic', 'DEVELOPMENT_TEAM': '',
            'CURRENT_PROJECT_VERSION': '1', 'MARKETING_VERSION': '1.0.0', 'GENERATE_INFOPLIST_FILE': 'NO',
            'INFOPLIST_FILE': 'Shiying/Info.plist', 'IPHONEOS_DEPLOYMENT_TARGET': '16.0',
            'PRODUCT_BUNDLE_IDENTIFIER': 'com.aojiao.shiying.ios', 'PRODUCT_NAME': '$(TARGET_NAME)',
            'SUPPORTED_PLATFORMS': 'iphoneos iphonesimulator', 'SUPPORTS_MACCATALYST': 'NO',
            'TARGETED_DEVICE_FAMILY': '1,2', 'SWIFT_VERSION': '5.0', 'SWIFT_STRICT_CONCURRENCY': 'minimal',
            'SWIFT_OPTIMIZATION_LEVEL': '-Onone' if name == 'Debug' else '-O',
            'SWIFT_ACTIVE_COMPILATION_CONDITIONS': 'DEBUG' if name == 'Debug' else '',
            'LD_RUNPATH_SEARCH_PATHS': ['$(inherited)', '@executable_path/Frameworks'], 'ENABLE_PREVIEWS': 'YES'}))
    project_list = add('project-configs', isa='XCConfigurationList', buildConfigurations=project_configs, defaultConfigurationIsVisible=0, defaultConfigurationName='Release')
    target_list = add('target-configs', isa='XCConfigurationList', buildConfigurations=target_configs, defaultConfigurationIsVisible=0, defaultConfigurationName='Release')
    target = add('target', isa='PBXNativeTarget', buildConfigurationList=target_list, buildPhases=phases, buildRules=[], dependencies=[], name='Shiying', productName='Shiying', productReference=product, productType='com.apple.product-type.application')
    project = add('project', isa='PBXProject', attributes={'LastUpgradeCheck': '1600', 'TargetAttributes': {target: {'CreatedOnToolsVersion': '16.0'}}},
                  buildConfigurationList=project_list, compatibilityVersion='Xcode 14.0', developmentRegion='zh-Hans', knownRegions=['zh-Hans', 'en', 'Base'],
                  mainGroup=root_group, productRefGroup=product_group, projectDirPath='', projectRoot='', targets=[target])
    folder = ROOT / 'Shiying.xcodeproj'
    folder.mkdir(exist_ok=True)
    document = {'archiveVersion': 1, 'classes': {}, 'objectVersion': 56, 'objects': objects, 'rootObject': project}
    (folder / 'project.pbxproj').write_text('// !$*UTF8*$!\n' + encode(document) + '\n', encoding='utf-8')
    schemes = folder / 'xcshareddata' / 'xcschemes'
    schemes.mkdir(parents=True, exist_ok=True)
    ref = f'<BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{target}" BuildableName="Shiying.app" BlueprintName="Shiying" ReferencedContainer="container:Shiying.xcodeproj"/>'
    scheme = f'''<?xml version="1.0" encoding="UTF-8"?>
<Scheme LastUpgradeVersion="1600" version="1.3">
 <BuildAction parallelizeBuildables="YES" buildImplicitDependencies="YES"><BuildActionEntries><BuildActionEntry buildForTesting="YES" buildForRunning="YES" buildForProfiling="YES" buildForArchiving="YES" buildForAnalyzing="YES">{ref}</BuildActionEntry></BuildActionEntries></BuildAction>
 <TestAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" shouldUseLaunchSchemeArgsEnv="YES"><Testables/></TestAction>
 <LaunchAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" launchStyle="0" useCustomWorkingDirectory="NO" ignoresPersistentStateOnLaunch="NO" debugDocumentVersioning="YES" debugServiceExtension="internal" allowLocationSimulation="YES"><BuildableProductRunnable runnableDebuggingMode="0">{ref}</BuildableProductRunnable></LaunchAction>
 <ProfileAction buildConfiguration="Release" shouldUseLaunchSchemeArgsEnv="YES" savedToolIdentifier="" useCustomWorkingDirectory="NO" debugDocumentVersioning="YES"><BuildableProductRunnable runnableDebuggingMode="0">{ref}</BuildableProductRunnable></ProfileAction>
 <AnalyzeAction buildConfiguration="Debug"/>
 <ArchiveAction buildConfiguration="Release" revealArchiveInOrganizer="YES"/>
</Scheme>
'''
    (schemes / 'Shiying.xcscheme').write_text(scheme, encoding='utf-8')
    # Code-native geometric app icon; opaque RGB, no external images or tools.
    size = 1024
    rows = []
    for y in range(size):
        row = bytearray([0])
        for x in range(size):
            radius = ((x - 512) ** 2 + (y - 468) ** 2) ** 0.5
            ring = 288 < radius < 332
            shaft = 470 <= x <= 554 and 250 <= y <= 530
            arrow = 490 <= y <= 662 and abs(x - 512) <= 662 - y
            base = 320 <= x <= 704 and 744 <= y <= 794
            if ring or shaft or arrow or base:
                row.extend((66, 232, 194))
            else:
                row.extend((12, 22 + y * 10 // size, 37 + y * 12 // size))
        rows.append(bytes(row))
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)
    png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', size, size, 8, 2, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(b''.join(rows), 9)) + chunk(b'IEND', b'')
    catalog = ROOT / 'Shiying' / 'Assets.xcassets'
    icon = catalog / 'AppIcon.appiconset'
    icon.mkdir(parents=True, exist_ok=True)
    (icon / 'AppIcon.png').write_bytes(png)
    (catalog / 'Contents.json').write_text(json.dumps({'info': {'author': 'xcode', 'version': 1}}, indent=2) + '\n')
    (icon / 'Contents.json').write_text(json.dumps({'images': [{'filename': 'AppIcon.png', 'idiom': 'universal', 'platform': 'ios', 'size': '1024x1024'}], 'info': {'author': 'xcode', 'version': 1}}, indent=2) + '\n')
    print(json.dumps({'project_objects': len(objects), 'swift_sources': len(sources), 'resource_entries': len(resource_builds)}, ensure_ascii=False))

if __name__ == '__main__':
    generate()
