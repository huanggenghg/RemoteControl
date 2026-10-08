"""Inspect actual DEX class definitions and native libraries in all four built APKs."""
import hashlib
import json
import struct
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

def dex_classes(data):
    assert data[:4] == b'dex\n'
    def u32(offset):
        return struct.unpack_from('<I', data, offset)[0]
    strings_offset, types_offset = u32(60), u32(68)
    class_count, classes_offset = u32(96), u32(100)
    def descriptor(type_id):
        string_id = u32(types_offset + type_id * 4)
        offset = u32(strings_offset + string_id * 4)
        while data[offset] & 0x80:
            offset += 1
        offset += 1
        return data[offset:data.index(b'\0', offset)].decode('utf-8', errors='replace')
    return [descriptor(u32(classes_offset + i * 32)) for i in range(class_count)]

records = []
for provider in ('zego', 'agora'):
    wanted = 'Lim/zego/' if provider == 'zego' else 'Lio/agora/'
    foreign = 'Lio/agora/' if provider == 'zego' else 'Lim/zego/'
    other = 'agora' if provider == 'zego' else 'zego'
    for variant in ('debug', 'release'):
        suffix = '-unsigned' if variant == 'release' else ''
        apk = ROOT / f'app/build/outputs/apk/{provider}/{variant}/app-{provider}-{variant}{suffix}.apk'
        with zipfile.ZipFile(apk) as z:
            names = z.namelist()
            classes = [c for name in names if name.startswith('classes') and name.endswith('.dex')
                       for c in dex_classes(z.read(name))]
            natives = sorted(name for name in names if name.startswith('lib/') and name.endswith('.so'))
            own_classes = [c for c in classes if c.startswith(wanted)]
            foreign_classes = [c for c in classes if c.startswith(foreign)]
            own_natives = [n for n in natives if provider in n.lower()]
            foreign_natives = [n for n in natives if other in n.lower()]
            assert own_classes and own_natives, (apk, 'Expected SDK absent')
            assert not foreign_classes and not foreign_natives, (apk, 'Mixed SDKs')
            records.append(dict(apk=str(apk.relative_to(ROOT)), size_bytes=apk.stat().st_size,
                                sha256=hashlib.sha256(apk.read_bytes()).hexdigest(),
                                sdk_classes=len(own_classes), foreign_sdk_classes=len(foreign_classes),
                                native_libraries=natives, foreign_native_libraries=foreign_natives))
print(json.dumps(records, indent=2))
