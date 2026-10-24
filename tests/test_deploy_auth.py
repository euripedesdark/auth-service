import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('deploy_auth', Path(__file__).resolve().parents[1] / 'deploy_auth.py')
deploy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(deploy)


class AuthDeploymentTest(unittest.TestCase):
    def test_failed_activation_restores_previous_unit_release_and_enablement(self):
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            app, units, root = base / 'app', base / 'units', base / 'source'
            for path in [app / 'old', units, root / 'target/release']:
                path.mkdir(parents=True)
            binary = root / 'target/release/astral-auth-service'
            binary.write_bytes(b'new binary')
            binary.chmod(0o755)
            (app / 'current').symlink_to(app / 'old')
            unit = units / 'auth-service.service'
            unit.write_text('old unit')
            auth_env, oauth_env = base / 'auth.env', base / 'oauth.env'
            auth_env.write_text('KEEP=existing')
            oauth_env.write_text('OAUTH=existing')
            calls = []
            def ctl(*args, check=True):
                calls.append(args)
                if args[0] == 'show' and args[2] == '-p' and args[3] == 'LoadState':
                    output = 'loaded' if args[1] == 'auth-service.service' else 'not-found'
                elif args[0] == 'show':
                    # A preexisting Java drop-in overrides the new ExecStart.
                    output = '/usr/bin/java -jar old.jar'
                else: output = ''
                return subprocess.CompletedProcess(args, 0, output, '')
            original = Path.is_dir
            with patch.multiple(deploy, APP=app, UNITS=units, ROOT=root, AUTH_ENV=auth_env, OAUTH_ENV=oauth_env), \
                    patch.object(deploy.os, 'geteuid', return_value=0), \
                    patch.object(deploy, 'build'), patch.object(deploy, 'ctl', side_effect=ctl), \
                    patch.object(Path, 'is_dir', lambda p: True if str(p) == '/run/systemd/system' else original(p)):
                with self.assertRaisesRegex(RuntimeError, 'drop-in'):
                    deploy.main()
            self.assertEqual(unit.read_text(), 'old unit')
            self.assertEqual((app / 'current').resolve(), app / 'old')
            self.assertIn(('start', 'auth-service.service'), calls)
            self.assertEqual(auth_env.read_text(), 'KEEP=existing')
            self.assertEqual(oauth_env.read_text(), 'OAUTH=existing')


if __name__ == '__main__':
    unittest.main()
