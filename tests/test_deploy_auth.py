import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch, call

spec = importlib.util.spec_from_file_location('deploy_auth', Path(__file__).resolve().parents[1] / 'deploy_auth.py')
deploy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(deploy)


class AuthDeploymentTest(unittest.TestCase):
    def test_pull_is_fast_forward_and_reloads_updated_script(self):
        result = subprocess.CompletedProcess([], 0, 'main\n', '')
        with patch.object(deploy.subprocess, 'run', return_value=result), \
                patch.object(deploy, 'build') as build, \
                patch.object(deploy.os, 'execv') as execute, \
                patch.object(deploy.sys, 'argv', ['deploy_auth.py', '--install-deps']):
            deploy.update_source()
        build.assert_called_once_with(['git', 'pull', '--ff-only', 'origin', 'main'])
        self.assertEqual(execute.call_args.args[1][-2:], ['--install-deps', '--no-pull'])

    def test_refuses_pulling_main_into_a_different_branch(self):
        result = subprocess.CompletedProcess([], 0, 'feature/local\n', '')
        with patch.object(deploy.subprocess, 'run', return_value=result), \
                patch.object(deploy, 'build') as build:
            with self.assertRaisesRegex(RuntimeError, 'main'):
                deploy.update_source()
        build.assert_not_called()

    def test_dependency_install_uses_fedora_manager(self):
        with patch.object(deploy.shutil, 'which', side_effect=lambda name: '/usr/bin/dnf' if name == 'dnf' else None), \
                patch.object(deploy.subprocess, 'run') as run:
            deploy.install_dependencies()
        self.assertEqual(run.call_args.args[0][:3], ['/usr/bin/dnf', 'install', '-y'])

    def test_failed_activation_restores_previous_unit_release_and_enablement(self):
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            app, units, root = base / 'app', base / 'units', base / 'source'
            for path in [app / 'old', units, root / 'target/release', root / 'web/dist']:
                path.mkdir(parents=True)
            binary = root / 'target/release/astral-auth-service'
            (root / 'web/dist/index.html').write_text('<html>new frontend</html>')
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
                    patch.object(deploy.sys, 'argv', ['deploy_auth.py', '--no-pull']), \
                    patch.object(deploy, 'build') as build, patch.object(deploy, 'ctl', side_effect=ctl), \
                    patch.object(Path, 'is_dir', lambda p: True if str(p) == '/run/systemd/system' else original(p)):
                with self.assertRaisesRegex(RuntimeError, 'drop-in'):
                    deploy.main()
            self.assertEqual(build.call_args_list, [
                call(['cargo', 'test', '--locked']),
                call(['cargo', 'build', '--release', '--locked']),
                call(['npm', 'ci'], cwd=root / 'web'),
                call(['npm', 'run', 'build'], cwd=root / 'web'),
            ])
            releases = list((app / 'releases').iterdir())
            self.assertEqual((releases[0] / 'web/index.html').read_text(), '<html>new frontend</html>')
            self.assertEqual(unit.read_text(), 'old unit')
            self.assertEqual((app / 'current').resolve(), app / 'old')
            self.assertIn(('start', 'auth-service.service'), calls)
            self.assertEqual(auth_env.read_text(), 'KEEP=existing')
            self.assertEqual(oauth_env.read_text(), 'OAUTH=existing')


if __name__ == '__main__':
    unittest.main()
