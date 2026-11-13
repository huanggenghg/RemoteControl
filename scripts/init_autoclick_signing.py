#!/usr/bin/env python3
"""Create the FIRST AutoClick release key. Never replace an existing key."""

import argparse
import hashlib
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--private-dir", required=True, type=Path,
                        help="New private directory OUTSIDE this checkout; retain a private backup.")
    args = parser.parse_args()
    private = args.private_dir.expanduser().resolve()
    properties = ROOT / "autoclick/signing.properties"
    pin = ROOT / "autoclick/release-certificate.sha256"
    certificate = ROOT / "autoclick/release-certificate.pem"
    if private == ROOT or ROOT in private.parents:
        parser.error("Keep the private directory outside the source checkout.")
    if private.exists() or properties.exists() or pin.exists() or certificate.exists():
        parser.error("Signing files already exist. Reuse and verify them; no files were replaced.")
    keytool = shutil.which("keytool")
    if not keytool:
        parser.error("keytool is required (install a JDK).")
    private.mkdir(mode=0o700, parents=True)
    password = secrets.token_urlsafe(36)
    keystore = private / "autoclick-release.p12"
    env = dict(os.environ, AUTOCLICK_INITIAL_KEY_PASSWORD=password)
    command = [keytool, "-genkeypair", "-noprompt", "-storetype", "PKCS12",
               "-keystore", str(keystore), "-alias", "autoclick", "-keyalg", "RSA",
               "-keysize", "3072", "-sigalg", "SHA256withRSA", "-validity", "10000",
               "-dname", "CN=AutoClick", "-storepass:env", "AUTOCLICK_INITIAL_KEY_PASSWORD",
               "-keypass:env", "AUTOCLICK_INITIAL_KEY_PASSWORD"]
    subprocess.run(command, env=env, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    keystore.chmod(0o600)
    # A Java properties file uses ISO-8859-1 with Unicode escapes.
    escaped = ""
    for char in str(keystore):
        if char in "\\: =#!":
            escaped += "\\" + char
        elif 32 <= ord(char) < 127:
            escaped += char
        else:
            escaped += "".join("\\u" + char.encode("utf-16-be")[offset:offset + 2].hex()
                               for offset in range(0, len(char.encode("utf-16-be")), 2))
    config = (f"storeFile={escaped}\nstorePassword={password}\n"
              f"keyAlias=autoclick\nkeyPassword={password}\n")
    backup_config = private / "signing.properties"
    with os.fdopen(os.open(backup_config, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), "w", encoding="ascii") as output:
        output.write(config)
    with os.fdopen(os.open(properties, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), "w", encoding="ascii") as output:
        output.write(config)
    export = [keytool, "-exportcert", "-keystore", str(keystore), "-alias", "autoclick",
              "-storepass:env", "AUTOCLICK_INITIAL_KEY_PASSWORD"]
    der = subprocess.check_output(export, env=env, stderr=subprocess.PIPE)
    certificate.write_bytes(subprocess.check_output(export + ["-rfc"], env=env, stderr=subprocess.PIPE))
    pin.write_text(hashlib.sha256(der).hexdigest() + "\n", encoding="ascii")
    print(f"Created a new release key in {private}")
    print("The keystore and signing.properties are private. Back up BOTH outside this environment.")
    print("Only the public certificate and SHA-256 pin belong in source control.")


if __name__ == "__main__":
    try:
        main()
    except subprocess.CalledProcessError:
        sys.exit("Key generation/export failed. Retain any created private files and inspect locally; secrets were not printed.")
