#!/usr/bin/env python3
"""Deploy the OAuth-capable Rust auth-service, keeping auth.env and restoring on failure."""
import os
import argparse
from pathlib import Path
import pwd
import shutil
import subprocess
import sys
import tempfile
import time
import json
import fcntl
from urllib.request import urlopen

ROOT = Path(__file__).resolve().parent
APP = Path('/opt/astral-auth-service')
UNITS = Path('/etc/systemd/system')
AUTH_ENV = Path('/etc/auth-service/auth.env')
OAUTH_ENV = Path('/etc/auth-service/oauth2.env')


def ctl(*args, check=True):
    result = subprocess.run(['systemctl', *args], capture_output=True, text=True)
    if check and result.returncode:
        raise RuntimeError('systemctl ' + ' '.join(args) + ': ' + result.stderr)
    return result


def snapshot(path):
    if path.is_symlink(): return ('link', os.readlink(path))
    if path.exists(): return ('file', path.read_bytes(), path.stat().st_mode & 0o777)
    return None


def restore(path, state):
    path.unlink(missing_ok=True)
    if state:
        path.parent.mkdir(parents=True, exist_ok=True)
        if state[0] == 'link': path.symlink_to(state[1])
        else:
            path.write_bytes(state[1]); path.chmod(state[2])


def build(args, cwd=None):
    user = pwd.getpwnam(os.environ.get('SUDO_USER', 'root'))
    env = os.environ.copy()
    env.update(HOME=user.pw_dir, USER=user.pw_name, LOGNAME=user.pw_name,
               PATH=user.pw_dir + '/.cargo/bin:' + os.environ.get('PATH', ''))
    def demote():
        if user.pw_uid:
            os.initgroups(user.pw_name, user.pw_gid)
            os.setgid(user.pw_gid); os.setuid(user.pw_uid)
    print('+ ' + ' '.join(args), flush=True)
    subprocess.run(args, cwd=cwd or ROOT, env=env, preexec_fn=demote, check=True)


def install_dependencies():
    if shutil.which('dnf') or shutil.which('yum'):
        manager = shutil.which('dnf') or shutil.which('yum')
        command = [manager, 'install', '-y', 'gcc', 'gcc-c++', 'make', 'pkgconf-pkg-config',
                   'openssl-devel', 'git', 'nodejs', 'npm', 'cargo', 'rust']
    elif shutil.which('apt-get'):
        subprocess.run(['apt-get', 'update'], check=True)
        command = ['apt-get', 'install', '-y', 'build-essential', 'pkg-config',
                   'libssl-dev', 'git', 'nodejs', 'npm', 'cargo', 'rustc']
    else:
        raise RuntimeError('Gerenciador de pacotes nao suportado; instale Rust, Node.js/npm, compilador C e OpenSSL de desenvolvimento.')
    subprocess.run(command, check=True)


def update_source():
    branch = subprocess.run(['git', '-c', 'safe.directory=' + str(ROOT), 'branch', '--show-current'],
                            cwd=ROOT, capture_output=True, text=True, check=True).stdout.strip()
    if branch != 'main':
        raise RuntimeError('Execute em main; nenhuma branch sera trocada automaticamente.')
    build(['git', 'pull', '--ff-only', 'origin', 'main'])
    # Reload the script after pulling so deployment uses the updated implementation.
    os.execv(sys.executable, [sys.executable, str(ROOT / 'deploy_auth.py'), *sys.argv[1:], '--no-pull'])


def main():
    parser = argparse.ArgumentParser(description='Atualiza, testa, compila e publica backend/frontend do auth-service com rollback.')
    parser.add_argument('--no-pull', action='store_true', help='Usa o checkout atual, sem atualizar o Git')
    parser.add_argument('--install-deps', action='store_true', help='Instala dependencias via dnf/yum ou apt')
    args = parser.parse_args()
    if os.geteuid() != 0:
        os.execvp('sudo', ['sudo', sys.executable, str(Path(__file__).resolve()), *sys.argv[1:]])
    if not args.no_pull: update_source()
    if args.install_deps: install_dependencies()
    if not Path('/run/systemd/system').is_dir(): raise RuntimeError('systemd necessario')
    if not AUTH_ENV.is_file(): raise RuntimeError('Configure /etc/auth-service/auth.env com LDAP/TLS e segredo JWT existentes antes de implantar.')
    if not OAUTH_ENV.is_file(): raise RuntimeError('Execute configure_oauth2.py no HCI para registrar o cliente.')
    names = ['auth-service.service', 'astral-auth-service.service', 'astral-auth.service']
    loaded = [n for n in names if ctl('show', n, '-p', 'LoadState', '--value', check=False).stdout.strip() == 'loaded']
    active = [n for n in loaded if ctl('is-active', n, check=False).returncode == 0]
    if len(active) > 1: raise RuntimeError('Mais de um auth-service ativo; escolha/consolide a unit antes de implantar.')
    unit = (active or loaded or names)[0]
    APP.mkdir(parents=True, exist_ok=True)
    with (APP / 'deploy.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        build(['cargo', 'test', '--locked'])
        build(['cargo', 'build', '--release', '--locked'])
        build(['npm', 'ci'], cwd=ROOT / 'web')
        build(['npm', 'run', 'build'], cwd=ROOT / 'web')
        if not (ROOT / 'web/dist/index.html').is_file():
            raise RuntimeError('Build do frontend nao gerou web/dist/index.html')
        releases = APP / 'releases'; releases.mkdir(exist_ok=True)
        release = Path(tempfile.mkdtemp(prefix=time.strftime('%Y%m%d-%H%M%S-'), dir=releases))
        shutil.copy2(ROOT / 'target/release/astral-auth-service', release / 'astral-auth-service')
        shutil.copytree(ROOT / 'web/dist', release / 'web')
        current = APP / 'current'
        if current.exists() and not current.is_symlink(): raise RuntimeError('current deve ser um link simbolico')
        paths = [UNITS / unit, UNITS / 'multi-user.target.wants' / unit, current]
        saved = {p: snapshot(p) for p in paths}
        history = APP / 'history'; history.mkdir(exist_ok=True)
        backup = Path(tempfile.mkdtemp(prefix=time.strftime('%Y%m%d-%H%M%S-'), dir=history))
        for index, (path, state) in enumerate(saved.items()):
            if state and state[0] == 'file':
                dest = backup / f'{index}-{path.name}'; dest.write_bytes(state[1]); dest.chmod(0o600)
        try:
            if active: ctl('stop', unit)
            target = UNITS / unit; target.unlink(missing_ok=True)
            target.write_text(f'''[Unit]
Description=Astral Auth Service (Rust OAuth2 + AD)
After=network-online.target
Wants=network-online.target
[Service]
Type=simple
WorkingDirectory={APP}/current
EnvironmentFile={AUTH_ENV}
EnvironmentFile={OAUTH_ENV}
Environment=AUTH_WEB_DIST={APP}/current/web
ExecStart={APP}/current/astral-auth-service
Restart=on-failure
RestartSec=3
TimeoutStopSec=30
UMask=0027
[Install]
WantedBy=multi-user.target
''')
            next_link = APP / 'current.next'; next_link.unlink(missing_ok=True); next_link.symlink_to(release); next_link.replace(current)
            ctl('daemon-reload')
            effective = ctl('show', unit, '-p', 'ExecStart', '--value').stdout
            if str(APP / 'current/astral-auth-service') not in effective: raise RuntimeError('Um drop-in ainda sobrescreve ExecStart do auth-service; restaurando unit anterior.')
            ctl('enable', unit); ctl('reset-failed', unit, check=False); ctl('start', unit)
            deadline = time.monotonic() + 60
            while True:
                try:
                    ctl('is-active', unit)
                    with urlopen('http://127.0.0.1:8181/health', timeout=3) as r:
                        if json.load(r).get('status') != 'UP': raise RuntimeError('health')
                    with urlopen('http://127.0.0.1:8181/.well-known/oauth-authorization-server', timeout=3) as r:
                        metadata = json.load(r)
                        if 'S256' not in metadata.get('code_challenge_methods_supported', []): raise RuntimeError('OAuth2/PKCE ausente')
                    with urlopen('http://127.0.0.1:8181/admin/', timeout=3) as r:
                        if 'text/html' not in r.headers.get('Content-Type', ''):
                            raise RuntimeError('Frontend indisponivel')
                    break
                except Exception:
                    if time.monotonic() > deadline: raise RuntimeError('Auth-service/OAuth2 nao ficou pronto; consulte journalctl -u ' + unit)
                    time.sleep(1)
        except BaseException:
            ctl('stop', unit, check=False)
            for path, state in saved.items(): restore(path, state)
            ctl('daemon-reload', check=False)
            if active: ctl('start', unit, check=False)
            raise
        print('OK: auth-service OAuth2 em 127.0.0.1:8181; unit ' + unit)


if __name__ == '__main__':
    try: main()
    except Exception as error:
        print('ERRO: ' + str(error), file=sys.stderr); sys.exit(1)
