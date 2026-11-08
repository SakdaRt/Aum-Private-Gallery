#!/usr/bin/env python3
"""Isolated local crypt + HTTP contract test. Never opens a user's rclone config."""
import base64
import hashlib
import os
from pathlib import Path
import secrets
import socket
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request

binary = str(Path(sys.argv[1]).resolve())
with tempfile.TemporaryDirectory(prefix='aum-crypt-test-') as temp:
    root = Path(temp)
    config = root / 'test.conf'
    password = subprocess.check_output([binary, 'obscure', secrets.token_hex(32)], text=True).strip()
    config.write_text('[vault]\ntype = crypt\nremote = ' + str(root / 'ciphertext') +
                      '\nfilename_encryption = standard\ndirectory_name_encryption = true\npassword = ' + password + '\n')
    os.chmod(config, 0o600)
    def run(*args, **kwargs):
        return subprocess.run([binary, '--config', str(config), '--log-level', 'ERROR', *args], check=True,
                              stdout=subprocess.PIPE, stderr=subprocess.PIPE, **kwargs)
    payload = secrets.token_bytes(8 * 1024 * 1024)
    run('rcat', 'vault:PrivateLibrary/videos/12345678-1234-1234-1234-123456789abc.mp4', input=payload)
    assert run('cat', 'vault:PrivateLibrary/videos/12345678-1234-1234-1234-123456789abc.mp4').stdout == payload
    raw_files = [p for p in (root / 'ciphertext').rglob('*') if p.is_file()]
    assert raw_files and not any('.mp4' in p.name for p in raw_files)
    assert raw_files[0].read_bytes().startswith(b'RCLONE\x00\x00')
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        port = sock.getsockname()[1]
    user, secret, prefix = secrets.token_hex(16), secrets.token_hex(32), secrets.token_hex(24)
    auth = 'Basic ' + base64.b64encode(f'{user}:{secret}'.encode()).decode()
    url = f'http://127.0.0.1:{port}/{prefix}/12345678-1234-1234-1234-123456789abc.mp4'
    proc = subprocess.Popen([binary, '--config', str(config), '--log-level', 'ERROR', 'serve', 'http',
                             'vault:PrivateLibrary/videos', '--addr', f'127.0.0.1:{port}',
                             '--user', user, '--pass', secret, '--baseurl', '/' + prefix,
                             '--vfs-cache-mode', 'off'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    try:
        for attempt in range(60):
            try:
                req = urllib.request.Request(url, method='HEAD', headers={'Authorization': auth})
                with opener.open(req, timeout=2) as response:
                    assert response.status == 200
                break
            except (OSError, urllib.error.URLError):
                if proc.poll() is not None:
                    raise RuntimeError('rclone server exited')
                time.sleep(.1)
        else:
            raise RuntimeError('Server startup timeout')
        for supplied in (None, 'Basic ' + base64.b64encode(b'wrong:wrong').decode()):
            try:
                opener.open(urllib.request.Request(url, headers={} if supplied is None else {'Authorization': supplied}), timeout=2)
                raise AssertionError('Unauthenticated access allowed')
            except urllib.error.HTTPError as error:
                assert error.code == 401
        for start in (0, len(payload)//2, len(payload)-1024):
            end = start + 1023
            req = urllib.request.Request(url, headers={'Authorization': auth, 'Range': f'bytes={start}-{end}'})
            with opener.open(req, timeout=5) as response:
                body = response.read()
                assert response.status == 206 and len(body) == 1024
                assert response.headers['Content-Range'] == f'bytes {start}-{end}/{len(payload)}'
                assert body == payload[start:end+1]
        print('PASS: crypt roundtrip; ciphertext names/header; missing/wrong auth=401; authenticated HEAD=200; start/middle/end ranges=206 and 1024 exact bytes')
    finally:
        proc.terminate()
        proc.wait(timeout=10)
    print('PASS: isolated server terminated and temporary test vault cleaned on exit')
