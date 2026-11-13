import importlib.util
from pathlib import Path
import unittest

MODULE_PATH = Path(__file__).resolve().parents[1] / "package_autoclick.py"
SPEC = importlib.util.spec_from_file_location("package_autoclick", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)

MANIFEST = '''<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.lumostech.autoclick" android:versionName="1.0.1" android:versionCode="2">
    <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM"/>
    <application android:allowBackup="false" android:fullBackupContent="false"
        android:dataExtractionRules="@xml/data_extraction_rules">
        <activity android:name="com.lumostech.autoclick.LocalInfoActivity" android:exported="false"/>
        <service android:name="com.lumostech.accessibilitycore.AccessibilityCoreService"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
            <meta-data android:name="android.accessibilityservice" android:resource="@xml/accessibility_config"/>
        </service>
    </application>
</manifest>'''
ACCESSIBILITY = '''<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:canPerformGestures="true" android:canTakeScreenshot="false"/>'''
EXTRACTION = "<data-extraction-rules>" + "".join(
    "<" + mode + ">" + "".join('<exclude domain="' + domain + '" path="."/>'
                               for domain in sorted(MODULE.BACKUP_DOMAINS)) + "</" + mode + ">"
    for mode in ("cloud-backup", "device-transfer")) + "</data-extraction-rules>"


class OfflineDistributionGuardTest(unittest.TestCase):
    def audit(self, manifest=MANIFEST, accessibility=ACCESSIBILITY, extraction=EXTRACTION):
        return MODULE.audit_manifest(manifest, accessibility, extraction)

    def test_offline_package_keeps_clicking_and_local_information(self):
        result = self.audit()
        self.assertFalse(result["network_access"])
        self.assertEqual(result["version_name"], "1.0.1")

    def test_transitive_network_or_other_unexpected_permission_is_rejected(self):
        for tag in ("uses-permission", "uses-permission-sdk-23", "uses-permission-sdk-m"):
            for permission in ("INTERNET", "ACCESS_NETWORK_STATE", "CAMERA", "RECORD_AUDIO"):
                with self.subTest(tag=tag, permission=permission), self.assertRaisesRegex(ValueError, "Unexpected permissions"):
                    self.audit(MANIFEST.replace("</manifest>",
                        '<' + tag + ' android:name="android.permission.' + permission + '"/></manifest>'))

    def test_debug_and_test_packages_are_rejected(self):
        for flag in ("debuggable", "testOnly"):
            with self.subTest(flag=flag), self.assertRaises(ValueError):
                self.audit(MANIFEST.replace("<application ", '<application android:' + flag + '="true" '))

    def test_screenshot_capability_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "Screenshot capability"):
            self.audit(accessibility=ACCESSIBILITY.replace('canTakeScreenshot="false"', 'canTakeScreenshot="true"'))

    def test_legacy_backup_is_rejected(self):
        for field in ("allowBackup", "fullBackupContent"):
            with self.subTest(field=field), self.assertRaises(ValueError):
                self.audit(MANIFEST.replace(field + '="false"', field + '="true"'))

    def test_missing_cloud_or_transfer_exclusion_is_rejected(self):
        for mode in ("cloud-backup", "device-transfer"):
            with self.subTest(mode=mode), self.assertRaises(ValueError):
                self.audit(extraction=EXTRACTION.replace('<' + mode + '>', '<' + mode + '><include domain="file" path="."/>'))
        with self.assertRaisesRegex(ValueError, "Incomplete"):
            self.audit(extraction=EXTRACTION.replace('<exclude domain="file" path="."/>', ""))

    def test_public_information_activity_cannot_be_exported(self):
        with self.assertRaisesRegex(ValueError, "information activity"):
            self.audit(MANIFEST.replace('android:exported="false"', 'android:exported="true"'))

    def test_accessibility_service_requires_system_binding_permission(self):
        with self.assertRaisesRegex(ValueError, "system permission"):
            self.audit(MANIFEST.replace('android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"', ""))

    def test_local_graphics_library_is_allowed_but_communication_sdk_is_rejected(self):
        graphics = "lib/arm64-v8a/libandroidx.graphics.path.so"
        self.assertEqual(MODULE.audit_native_libraries([graphics, "classes.dex"]), [graphics])
        with self.assertRaisesRegex(ValueError, "Unexpected native SDK"):
            MODULE.audit_native_libraries([graphics, "lib/arm64-v8a/libzegoexpress.so"])

    def test_shortened_apk_paths_are_resolved_from_actual_resource_table(self):
        references, paths = MODULE.resource_map('''
    resource 0x7f140000 xml/accessibility_config
      () (file) res/az.xml type=XML
    resource 0x7f100000 raw/privacy_policy
      () (file) res/Oo.txt
''')
        self.assertEqual(references["@ref/0x7f140000"], "@xml/accessibility_config")
        self.assertEqual(paths["raw/privacy_policy"], "res/Oo.txt")

    def test_compiled_policy_preserves_false_and_android_namespace(self):
        xml = MODULE.tree_xml('''N: android=http://schemas.android.com/apk/res/android
  E: accessibility-service (line=1)
    A: http://schemas.android.com/apk/res/android:canTakeScreenshot(0x0101060f)=false
    A: http://schemas.android.com/apk/res/android:canPerformGestures(0x0101050d)=true
''')
        root = MODULE.ET.fromstring(xml)
        self.assertEqual(root.get(MODULE.A + "canTakeScreenshot"), "false")
        self.assertEqual(root.get(MODULE.A + "canPerformGestures"), "true")


if __name__ == "__main__":
    unittest.main()
