#!/usr/bin/env python3
"""Audit a signed offline APK and create the public first-user distribution."""

import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import ssl
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
A = "{http://schemas.android.com/apk/res/android}"
PACKAGE = "com.lumostech.autoclick"
ALLOWED_PERMISSIONS = {
    "android.permission.SCHEDULE_EXACT_ALARM", "android.permission.RECEIVE_BOOT_COMPLETED",
    "android.permission.SYSTEM_ALERT_WINDOW", "android.permission.WAKE_LOCK",
    "android.permission.FOREGROUND_SERVICE", PACKAGE + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
}
BACKUP_DOMAINS = {"root", "file", "database", "sharedpref", "external", "device_root",
                  "device_file", "device_database", "device_sharedpref"}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def audit_manifest(manifest_xml, accessibility_xml, extraction_xml):
    manifest = ET.fromstring(manifest_xml)
    require(manifest.get("package") == PACKAGE, "Unexpected application ID")
    app = manifest.find("application")
    require(app is not None, "Missing application")
    permissions = sorted(node.get(A + "name", "") for node in manifest
                         if node.tag in ("uses-permission", "uses-permission-sdk-23", "uses-permission-sdk-m"))
    require(set(permissions) <= ALLOWED_PERMISSIONS,
            "Unexpected permissions: " + ", ".join(sorted(set(permissions) - ALLOWED_PERMISSIONS)))
    require(app.get(A + "debuggable", "false") == "false", "Debuggable APK cannot be distributed")
    require(app.get(A + "testOnly", "false") == "false", "Test-only APK cannot be distributed")
    require(app.get(A + "allowBackup") == "false", "System backup must be disabled")
    require(app.get(A + "fullBackupContent") == "false", "Legacy full backup must be disabled")
    require(app.get(A + "dataExtractionRules") == "@xml/data_extraction_rules", "Missing data extraction rules")
    info = next((node for node in app.findall("activity")
                 if node.get(A + "name") in (".LocalInfoActivity", PACKAGE + ".LocalInfoActivity")), None)
    require(info is not None and info.get(A + "exported") == "false", "Local information activity must be private")
    service = next((node for node in app.findall("service") if node.get(A + "name") ==
                    "com.lumostech.accessibilitycore.AccessibilityCoreService"), None)
    require(service is not None and service.get(A + "permission") == "android.permission.BIND_ACCESSIBILITY_SERVICE",
            "Accessibility service must be protected by the system permission")
    metadata = next((node for node in service.findall("meta-data")
                     if node.get(A + "name") == "android.accessibilityservice"), None)
    require(metadata is not None and metadata.get(A + "resource") == "@xml/accessibility_config",
            "Unexpected accessibility configuration")
    accessibility = ET.fromstring(accessibility_xml)
    require(accessibility.get(A + "canTakeScreenshot", "false") == "false", "Screenshot capability must be disabled")
    require(accessibility.get(A + "canPerformGestures") == "true", "Click gesture capability is missing")
    extraction = ET.fromstring(extraction_xml)
    for mode in ("cloud-backup", "device-transfer"):
        section = extraction.find(mode)
        require(section is not None, "Missing " + mode + " policy")
        require(not section.findall("include"), "Backup policy must not include application data")
        excluded = {node.get("domain") for node in section.findall("exclude") if node.get("path") == "."}
        require(BACKUP_DOMAINS <= excluded, "Incomplete " + mode + " exclusions")
    return {"application_id": PACKAGE, "version_name": manifest.get(A + "versionName"),
            "version_code": manifest.get(A + "versionCode"), "permissions": permissions,
            "network_access": False, "screenshot_capability": False,
            "system_backup": False, "system_device_transfer": False}


def command(args):
    return subprocess.check_output([str(arg) for arg in args], text=True, stderr=subprocess.PIPE).strip()


def audit_native_libraries(names):
    libraries = sorted(name for name in names if name.startswith("lib/") and name.endswith(".so"))
    require(all(Path(name).name == "libandroidx.graphics.path.so" for name in libraries),
            "Unexpected native SDK in the offline edition")
    return libraries


def resource_map(dump):
    """Resolve resource IDs and file paths, including AAPT's shortened APK paths."""
    references, files, current = {}, {}, None
    for line in dump.splitlines():
        resource = re.match(r"\s*resource (0x[0-9a-fA-F]+) (\S+)", line)
        if resource:
            current = resource.group(2).split(":")[-1]
            references["@ref/" + resource.group(1).lower()] = "@" + current
            continue
        file = re.match(r"\s*\(\) \(file\) (\S+)", line)
        if current and file:
            files[current] = file.group(1)
    return references, files


def tree_xml(dump):
    """Convert the SDK's XML tree output to XML for the small policy resources."""
    root, stack = None, []
    decoder = json.JSONDecoder()
    for line in dump.splitlines():
        element = re.match(r"(\s*)E: ([^\s(]+)", line)
        if element:
            indent = len(element.group(1))
            node = ET.Element(element.group(2))
            while stack and stack[-1][0] >= indent:
                stack.pop()
            if stack:
                stack[-1][1].append(node)
            else:
                require(root is None, "Multiple roots in APK XML resource")
                root = node
            stack.append((indent, node))
            continue
        attribute = re.match(r"\s*A: ([^(=]+)(?:\([^)]*\))?=(.*)", line)
        if attribute:
            require(bool(stack), "Attribute outside an APK XML element")
            name, value = attribute.group(1), attribute.group(2).strip()
            if name.startswith("http://schemas.android.com/apk/res/android:"):
                name = A + name.rsplit(":", 1)[1]
            elif name.startswith("android:"):
                name = A + name.split(":", 1)[1]
            if value.startswith('"'):
                value = decoder.raw_decode(value)[0]
            stack[-1][1].set(name, value)
    require(root is not None, "Cannot decode APK XML policy resource")
    return ET.tostring(root, encoding="unicode")


def inspect_apk(apk, analyzer, aapt):
    references, files = resource_map(command([aapt, "dump", "resources", apk]))
    for name in ("xml/accessibility_config", "xml/data_extraction_rules", "raw/privacy_policy", "raw/user_guide"):
        require(name in files, "Missing bundled resource: " + name)
    manifest = command([analyzer, "manifest", "print", apk])
    for value, name in references.items():
        manifest = manifest.replace(value, name)
    report = audit_manifest(manifest,
        tree_xml(command([aapt, "dump", "xmltree", "--file", files["xml/accessibility_config"], apk])),
        tree_xml(command([aapt, "dump", "xmltree", "--file", files["xml/data_extraction_rules"], apk])))
    return report, files


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path, help="New directory; existing output is never overwritten")
    args = parser.parse_args()
    apk = args.apk.resolve()
    output = args.output.resolve()
    require(apk.is_file(), "APK does not exist")
    require(not output.exists(), "Output already exists; use a new directory")
    sdk = Path(os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or "")
    analyzer = sdk / "cmdline-tools/latest/bin/apkanalyzer"
    tools = sorted((sdk / "build-tools").glob("*"), key=lambda p: tuple(int(n) for n in re.findall(r"\d+", p.name)))
    require(analyzer.is_file() and tools, "Set ANDROID_HOME to an SDK with command-line tools and build-tools")
    signer = tools[-1] / "apksigner"
    aapt = tools[-1] / "aapt2"
    signing = command([signer, "verify", "--verbose", "--print-certs", apk])
    require("Android Debug" not in signing, "A debug signing certificate cannot be distributed")
    digest = re.search(r"Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]+)", signing)
    require(digest is not None, "No verified signing certificate")
    require("Signer #2 certificate" not in signing, "Unexpected multiple signing certificates")
    pin = ROOT / "autoclick/release-certificate.sha256"
    require(pin.is_file(), "Pin the intended public certificate in autoclick/release-certificate.sha256 first")
    require(digest.group(1).lower() == pin.read_text().strip().lower(), "Signing certificate does not match the pinned release key")
    certificate = ROOT / "autoclick/release-certificate.pem"
    require(certificate.is_file(), "Public release certificate is missing")
    require(hashlib.sha256(ssl.PEM_cert_to_DER_cert(certificate.read_text())).hexdigest() == digest.group(1).lower(),
            "Public certificate does not match the verified APK")
    report, files = inspect_apk(apk, analyzer, aapt)
    command([tools[-1] / "zipalign", "-c", "-P", "16", "4", apk])
    report["zip_alignment_16k"] = True
    metadata = {}
    for field in ("autoclick_publisher", "autoclick_support_email"):
        value = command([analyzer, "resources", "value", "--config", "default", "--type", "string",
                         "--name", field, apk]).strip('"')
        require(value and "待确认" not in value, "Publisher name and feedback email must be confirmed before distribution")
        metadata[field] = value
    require("@" in metadata["autoclick_support_email"], "A public feedback email is required")
    with zipfile.ZipFile(apk) as archive:
        libraries = audit_native_libraries(archive.namelist())
        policy = archive.read(files["raw/privacy_policy"]).decode("utf-8")
        guide = archive.read(files["raw/user_guide"]).decode("utf-8")
    report.update({"checked_at_utc": datetime.now(timezone.utc).isoformat(),
                   "apk_sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
                   "certificate_sha256": digest.group(1).lower(),
                   "native_libraries": libraries,
                   "publisher": metadata["autoclick_publisher"], "support_email": metadata["autoclick_support_email"],
                   "source_commit": command(["git", "-C", ROOT, "rev-parse", "HEAD"]),
                   "source_has_uncommitted_changes": bool(command(["git", "-C", ROOT, "status", "--porcelain"])),
                   "physical_device_acceptance": "Not performed by this packaging check"})
    output.mkdir(parents=True)
    target = output / ("AutoClick-" + report["version_name"] + "-offline.apk")
    shutil.copyfile(apk, target)
    (output / "隐私与权限说明.txt").write_text(
        f"开发者：{report['publisher']}\n反馈邮箱：{report['support_email']}\n\n" + policy, encoding="utf-8")
    (output / "使用指南.txt").write_text(guide, encoding="utf-8")
    shutil.copyfile(ROOT / "docs/autoclick/first-users.md", output / "试用与反馈.md")
    (output / "release-audit.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (output / "signing-verification.txt").write_text(signing + "\n", encoding="utf-8")
    shutil.copyfile(certificate, output / "release-certificate.pem")
    files = sorted(path for path in output.iterdir() if path.is_file())
    (output / "SHA256SUMS").write_text("".join(hashlib.sha256(path.read_bytes()).hexdigest() + "  " + path.name + "\n"
                                                for path in files), encoding="utf-8")
    bundle = output.parent / (output.name + ".zip")
    require(not bundle.exists(), "Archive already exists; output directory retained")
    with zipfile.ZipFile(bundle, "x", compression=zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(output.iterdir()):
            archive.write(path, output.name + "/" + path.name)
    print(json.dumps({"apk": str(target), "bundle": str(bundle), "audit": str(output / "release-audit.json")}, ensure_ascii=False))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, ET.ParseError, KeyError, subprocess.CalledProcessError) as error:
        # Do not forward arbitrary child-process output or signing configuration.
        sys.exit("AutoClick packaging stopped: " + (str(error) if isinstance(error, ValueError) else type(error).__name__))
