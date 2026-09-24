#!/usr/bin/env python3
"""Build with a private local signing identity. Never prints credentials.

Set BANDTRAIL_SIGNING_DIR to a private folder containing release.jks and password,
or pass the three BANDTRAIL_* signing environment variables documented in README.
"""
import os
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[1]
env = os.environ.copy()
if not env.get("BANDTRAIL_KEYSTORE"):
    folder = Path(env.get("BANDTRAIL_SIGNING_DIR", str(Path.home() / ".local/share/bandtrail/signing")))
    key, password = folder / "release.jks", folder / "password"
    if not key.is_file() or not password.is_file():
        raise SystemExit("Signing files are missing; see README. No release was produced.")
    secret = password.read_text().strip()
    env.update(BANDTRAIL_KEYSTORE=str(key), BANDTRAIL_STORE_PASSWORD=secret, BANDTRAIL_KEY_PASSWORD=secret)
subprocess.run([str(root / "gradlew"), "testDebugUnitTest", "lintDebug", "assembleRelease"], cwd=root, env=env, check=True)
print("Signed APK: app/build/outputs/apk/release/app-release.apk")
