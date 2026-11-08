#!/usr/bin/env python3
"""Inspect actual APK bytes: ABI, native ELF alignment, hashes (not a device test)."""
import hashlib
from pathlib import Path
import struct
import sys
import zipfile

apk = Path(sys.argv[1])
print('APK:', apk.name)
print('BYTES:', apk.stat().st_size)
print('SHA256:', hashlib.sha256(apk.read_bytes()).hexdigest())
with zipfile.ZipFile(apk) as archive:
    assert archive.testzip() is None
    native = [n for n in archive.namelist() if n.startswith('lib/') and n.endswith('.so')]
    assert native and {n.split('/')[1] for n in native} == {'arm64-v8a'}
    for name in native:
        data = archive.read(name)
        assert data[:6] == b'\x7fELF\x02\x01', 'Expected little-endian ELF64'
        assert struct.unpack_from('<H', data, 18)[0] == 183, 'Expected AArch64'
        offset = struct.unpack_from('<Q', data, 32)[0]
        size, count = struct.unpack_from('<HH', data, 54)
        headers = [struct.unpack_from('<IIQQQQQQ', data, offset + i * size) for i in range(count)]
        loads = [h for h in headers if h[0] == 1]
        assert loads and all(h[7] >= 16384 for h in loads), f'{name}: LOAD alignment below16KiB'
        for relro in (h for h in headers if h[0] == 0x6474e552):
            start = relro[3]
            end = relro[3] + relro[6]
            rounded_start, rounded_end = start // 16384 * 16384, (end + 16383) // 16384 * 16384
            # RELRO may legally extend beyond a LOAD's actual memory range into padding.
            # Reject only when rounding would protect actual writable bytes outside RELRO.
            for load in (h for h in loads if h[1] & 2):
                lo, hi = load[3], load[3] + load[6]
                for padding_start, padding_end in ((rounded_start, start), (end, rounded_end)):
                    assert max(lo, padding_start) >= min(hi, padding_end), f'{name}: RELRO rounding covers writable bytes'
        print('ELF PASS:', name, 'LOAD aligns=' + ','.join(hex(h[7]) for h in loads), 'RELRO checked')
    print('PASS: APK ZIP integrity, ARM64-only native code, 16KiB ELF LOAD/RELRO checks')
