"""Exercise the real workflow shell with a fake signer and dummy credentials."""
import base64
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
import yaml

ROOT = Path(__file__).resolve().parents[2]
FLOW = yaml.safe_load((ROOT / ".github/workflows/release.yml").read_text())
BASH = Path("C:/Program Files/Git/bin/bash.exe") if os.name == "nt" else Path("/bin/bash")

class ReleaseSigningTest(unittest.TestCase):
    def test_all_shell_and_embedded_python_blocks_parse(self):
        with tempfile.TemporaryDirectory() as directory:
            script = Path(directory) / "step.sh"
            for job in FLOW["jobs"].values():
                for step in job.get("steps", []):
                    if "run" not in step:
                        continue
                    code = step["run"]
                    script.write_text(code, encoding="utf-8", newline="\n")
                    result = subprocess.run([str(BASH), "-n", script.as_posix()], capture_output=True, text=True)
                    self.assertEqual(result.returncode, 0, result.stderr)
                    if "<<'PY'" in code:
                        compile(code.split("<<'PY'", 1)[1].split("\nPY", 1)[0].lstrip("\n"), "workflow-python", "exec")

    def run_signing_step(self, failure):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory)
            tools = base / "sdk/build-tools/36.0.0"
            tools.mkdir(parents=True)
            runtime = base / "runtime"
            runtime.mkdir()
            binaries = base / "bin"
            binaries.mkdir()
            wrapper = binaries / "python3"
            wrapper.write_text('#!/usr/bin/env bash\nexec "' + Path(sys.executable).as_posix() + '" "$@"\n', newline="\n")
            wrapper.chmod(0o755)
            signer = tools / "apksigner"
            signer.write_text("""#!/usr/bin/env bash
set -euo pipefail
if [[ "$1" == sign ]]; then
  [[ "$KEYSTORE_PASSWORD" == dummy-store && "$KEY_PASSWORD" == dummy-key ]]
  keys=("$RUNNER_TEMP"/release-key.*)
  [[ ${#keys[@]} == 1 && -s "${keys[0]}" ]]
  if [[ "$MOCK_FAIL" == 1 ]]; then exit 42; fi
  cp aligned.apk signed/wallet.apk
else
  [[ -z "${KEYSTORE_PASSWORD+x}" && -z "${KEY_PASSWORD+x}" && -z "${KEY_ALIAS+x}" ]]
  if compgen -G "$RUNNER_TEMP/release-key.*" > /dev/null; then exit 43; fi
  echo 'Signer #1 certificate SHA-256 digest: 277c10bbed425c98b8df4aa7372dfe483daeefdc6ddbc19b462ac83df8d8b295'
fi
""", newline="\n")
            signer.chmod(0o755)
            (base / "aligned.apk").write_bytes(b"dummy data; this is not an APK or a real signature")
            step = next(x for x in FLOW["jobs"]["sign"]["steps"] if x.get("id") == "signature")
            script = base / "sign.sh"
            script.write_text(step["run"], newline="\n")
            env = dict(os.environ, ANDROID_HOME=(base / "sdk").as_posix(), RUNNER_TEMP=runtime.as_posix(),
                       GITHUB_OUTPUT=(base / "output.txt").as_posix(), KEYSTORE_BASE64=base64.b64encode(b"dummy keystore").decode(),
                       KEYSTORE_PASSWORD="dummy-store", KEY_PASSWORD="dummy-key", KEY_ALIAS="dummy-alias", MOCK_FAIL=str(int(failure)))
            env["PATH"] = str(binaries) + os.pathsep + env["PATH"]
            result = subprocess.run([str(BASH), script.as_posix()], cwd=base, env=env, capture_output=True, text=True)
            self.assertEqual(result.returncode, 42 if failure else 0, result.stderr)
            self.assertEqual(list(runtime.glob("release-key.*")), [], "Temporary key survived the signing step")
            if not failure:
                self.assertTrue((base / "output.txt").read_text().startswith("apk_sha256="))

    def test_key_cleanup_and_password_removal_on_success(self):
        self.run_signing_step(False)

    def test_key_cleanup_on_signing_failure(self):
        self.run_signing_step(True)

if __name__ == "__main__":
    unittest.main()
