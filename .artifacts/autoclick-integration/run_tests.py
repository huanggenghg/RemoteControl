"""Run focused emulator tests and restore the user's task and accessibility settings."""
import io
import hashlib
import json
import pathlib
import signal
import subprocess
import sys
import tarfile
import time
import xml.etree.ElementTree as ET

ADB = '/Users/hgeng/Library/Android/sdk/platform-tools/adb'
devices = subprocess.check_output([ADB, 'devices'], text=True).splitlines()[1:]
serials = [line.split()[0] for line in devices if line.endswith('\tdevice') and line.startswith('emulator-')]
assert len(serials) == 1, 'Expected one existing emulator'
BASE = [ADB, '-s', serials[0]]
OUT = pathlib.Path(__file__).resolve().parent
LABEL, CLASSES = sys.argv[1:3]
PROCESS_CHECK = len(sys.argv) > 3 and sys.argv[3] == 'process'

def interrupted(signum, _frame):
    raise InterruptedError(f'Runner interrupted by signal {signum}; restoring captured data')

signal.signal(signal.SIGTERM, interrupted)
if (OUT / 'backup-app.tar').exists() or (OUT / 'backup-settings.json').exists():
    marker = OUT / 'active-backup-label.txt'
    assert marker.exists() and (OUT / (marker.read_text().strip() + '-restore.txt')).exists(), \
        'Unrestored private backup exists; recover it before starting another batch'
(OUT / f'{LABEL}-restore.txt').unlink(missing_ok=True)


def call(*args, **kwargs):
    return subprocess.run(BASE + list(args), check=True, **kwargs)


def shell(*args):
    return call('shell', *args, capture_output=True).stdout.decode().strip()


def files(data):
    with tarfile.open(fileobj=io.BytesIO(data)) as archive:
        return {m.name: archive.extractfile(m).read() for m in archive.getmembers() if m.isfile()}


def saved_task(data):
    return next((e.text for e in ET.fromstring(data) if e.attrib.get('name') == 'task'), None)


def prefs(package, filename):
    data = call('exec-out', 'run-as', package, 'cat', 'shared_prefs/' + filename, capture_output=True).stdout
    return {e.attrib['name']: e.text if e.tag == 'string' else e.attrib.get('value') for e in ET.fromstring(data)}


def optional_task_xml(package):
    path = 'shared_prefs/autoclick_task.xml'
    result = subprocess.run(BASE + ['shell', 'run-as', package, 'test', '-f', path], capture_output=True)
    if result.returncode == 1:
        return b'<map />'
    result.check_returncode()
    return call('exec-out', 'run-as', package, 'cat', path, capture_output=True).stdout


def snapshot_app():
    paths = []
    for path in ['shared_prefs', 'databases', 'files', 'no_backup']:
        result = subprocess.run(BASE + ['shell', 'run-as', 'com.lumostech.autoclick', 'test', '-d', path], capture_output=True)
        if result.returncode == 0:
            paths.append(path)
        elif result.returncode != 1:
            result.check_returncode()
    if paths:
        return call('exec-out', 'run-as', 'com.lumostech.autoclick', 'tar', '-cf', '-', *paths, capture_output=True).stdout
    output = io.BytesIO()
    with tarfile.open(fileobj=output, mode='w'):
        pass
    return output.getvalue()


def ensure_installed(apk, package):
    expected = hashlib.sha256(pathlib.Path(apk).read_bytes()).hexdigest()
    result = subprocess.run(BASE + ['shell', 'pm', 'path', package], capture_output=True, text=True)
    paths = [line.removeprefix('package:') for line in result.stdout.splitlines() if line.startswith('package:')]
    actual = None
    if result.returncode == 0 and len(paths) == 1 and re.fullmatch(r'/data/app/[A-Za-z0-9_=/+.~\-]+/base\.apk', paths[0]):
        digest = shell('sha256sum', paths[0]).split()[0]
        if re.fullmatch(r'[0-9a-f]{64}', digest):
            actual = digest
    if actual != expected:
        call('install', '-r', apk)
        installed = shell('pm', 'path', package).removeprefix('package:').strip()
        assert re.fullmatch(r'/data/app/[A-Za-z0-9_=/+.~\-]+/base\.apk', installed)
        actual = shell('sha256sum', installed).split()[0]
    assert actual == expected, 'Installed APK does not match this build'
    print(f'APK VERIFIED: {package} sha256={actual}', flush=True)


def queued_exact_alarms(dump):
    import re
    alarms = []
    for block in re.split(r'(?m)(?=^\s+\w+ #\d+: Alarm\{)', dump):
        header = block.lstrip().splitlines()[0] if block.strip() else ''
        if 'RTC_WAKEUP #' not in header or 'com.lumostech.autoclick}' not in header:
            continue
        if 'tag=*walarm*:com.lumostech.autoclick.EXACT_CLICK' not in block:
            continue
        due = re.search(r'origWhen (\d+)', header)
        token = re.search(r'PendingIntentRecord\{([0-9a-f]+) com\.lumostech\.autoclick broadcastIntent}', block)
        if due and token and 'window=0 ' in block and 'repeatInterval=0 ' in block:
            alarms.append(dict(due=int(due.group(1)), token=token.group(1)))
    return alarms


def verify_registered_alarm(event, suffix):
    import re
    dump = shell('dumpsys', 'alarm')
    intents = shell('dumpsys', 'activity', 'intents')
    (OUT / f'{LABEL}-{suffix}-alarm.txt').write_text(dump)
    (OUT / f'{LABEL}-{suffix}-intents.txt').write_text(intents)
    matches = [a for a in queued_exact_alarms(dump) if a['due'] == event['scheduledAt']]
    assert len(matches) == 1, f'Expected one queued exact alarm for {event}, got {matches}'
    token = matches[0]['token']
    records = re.split(r'(?m)(?=^\s+#\d+: PendingIntentRecord\{)', intents)
    record = next((r for r in records if f'PendingIntentRecord{{{token} com.lumostech.autoclick broadcastIntent}}' in r), '')
    assert 'act=com.lumostech.autoclick.EXACT_CLICK' in record and 'ClickAlarmReceiver' in record
    assert 'dat=autoclick://com.lumostech.autoclick/' in record
    (OUT / f'{LABEL}-{suffix}-identity.json').write_text(json.dumps(dict(event=event, native_pending_intent_token=token, uri_path_redacted=True), indent=2))
    return token, dump


def verify_process_recovery():
    package = 'com.lumostech.autoclick'
    target = package + '.test'
    # Android force-stops the target on "finished inst", leaving this service
    # crashed. Restore its connection BEFORE the separate OS-kill experiment.
    enabled = shell('settings', '--user', '0', 'get', 'secure', 'enabled_accessibility_services')
    component = package + '/com.lumostech.accessibilitycore.AccessibilityCoreService'
    other = ':'.join(x for x in enabled.split(':') if x != component)
    if other:
        shell('settings', '--user', '0', 'put', 'secure', 'enabled_accessibility_services', other)
    else:
        shell('settings', '--user', '0', 'delete', 'secure', 'enabled_accessibility_services')
    shell('settings', '--user', '0', 'put', 'secure', 'enabled_accessibility_services', enabled)
    # Establish a normal, live app baseline after instrumentation has ended.
    shell('am', 'start', '-W', '-n', package + '/.MainActivity')
    click_text('停用任务'); click_text('启用任务')
    restored_schedule = json.loads(prefs(package, 'autoclick_task.xml')['exact_alarm'])['next']
    verify_registered_alarm(restored_schedule, 'before-pid-kill')
    shell('am', 'start', '-W', '-n', target + '/com.lumostech.autoclick.RecoveryTargetActivity')
    baseline_end = time.monotonic() + 10
    while True:
        baseline = shell('dumpsys', 'accessibility')
        if any('Bound services:' in line and 'label=AutoClick,' in line for line in baseline.splitlines()) and 'Ui Automation[' not in baseline:
            break
        if time.monotonic() >= baseline_end:
            break
        time.sleep(0.25)
    before = shell('pidof', package)
    assert before and ' ' not in before, 'Expected one application process'
    (OUT / f'{LABEL}-accessibility-before.txt').write_text(baseline)
    assert any('Bound services:' in line and 'label=AutoClick,' in line for line in baseline.splitlines()), 'Autoclick accessibility service is not bound'
    assert 'Ui Automation[' not in baseline, 'Instrumentation is still connected'
    assert int(prefs(target, 'recovery_target.xml')['clicks']) == 0
    seed_logs = shell('logcat', '-d', '-s', 'AutoclickProcessRecovery')
    import re
    due = restored_schedule['scheduledAt']
    assert 0 < due - int(time.time()*1000) < 90000, 'Process fixture must stay within quick wait budget'
    started = time.monotonic()
    # Signal the app's own PID, without setting Android's force-stopped flag.
    shell('run-as', package, 'kill', '-9', before)
    after = ''
    while time.monotonic() - started < 15:
        result = subprocess.run(BASE + ['shell', 'pidof', package], capture_output=True, text=True)
        after = result.stdout.strip()
        if after and after != before:
            break
        time.sleep(0.25)
    assert after and after != before, 'System did not restart the killed application process'
    rebound_seconds = round(time.monotonic() - started, 3)
    print(f'SYSTEM RECOVERY: PID {before} -> {after}, {rebound_seconds}s; waiting for the saved schedule.', flush=True)
    while time.monotonic() - started < 90:
        clicks = int(prefs(target, 'recovery_target.xml')['clicks'])
        state = prefs(package, 'autoclick_task.xml')
        if clicks >= 1 and state.get('outcome') == '全部点击手势已完成':
            break
        time.sleep(0.5)
    assert clicks == 1, f'Expected one actual button click, got {clicks}; state={state}'
    assert state.get('outcome') == '全部点击手势已完成', f'Worker did not complete: {state}'
    assert int(state['consumed_at']) == due
    trace = json.loads(state['start_trace'])
    button_at = int(prefs(target, 'recovery_target.xml')['first_click_at'])
    assert 0 <= trace['firstDispatchAt'] - due < 5000
    assert 0 <= button_at - due < 5000
    time.sleep(2)
    assert int(prefs(target, 'recovery_target.xml')['clicks']) == 1, 'Unexpected repeated click'
    recovery_logs = shell('logcat', '-d', '--pid=' + after, '-s', 'Autoclick/MyService', 'Autoclick/ClickPeriodicWorker', 'Autoclick/AutoclickApp')
    (OUT / f'{LABEL}-recovered-process.txt').write_text(recovery_logs)
    recovered_state = shell('dumpsys', 'accessibility')
    (OUT / f'{LABEL}-accessibility-after.txt').write_text(recovered_state)
    assert any('Bound services:' in line and 'label=AutoClick,' in line for line in recovered_state.splitlines())
    assert 'onServiceConnected' in recovery_logs, 'Missing service connection evidence from the new PID'
    call('shell', 'screencap', '-p', '/sdcard/autoclick-process-recovery.png')
    call('pull', '/sdcard/autoclick-process-recovery.png', str(OUT / f'{LABEL}.png'))
    evidence = dict(original_pid=before, recovered_pid=after, rebound_seconds=rebound_seconds,
                    scheduled_at=due, received_at=trace['receivedAt'], first_dispatch_at=trace['firstDispatchAt'],
                    button_received_at=button_at, clicks=clicks, outcome=state['outcome'],
                    elapsed_seconds=round(time.monotonic() - started, 3),
                    no_app_launch_after_kill=True, instrumentation_running_after_kill=False)
    (OUT / f'{LABEL}.json').write_text(json.dumps(evidence, ensure_ascii=False, indent=2))
    print(json.dumps(evidence, ensure_ascii=False), flush=True)


original_op = shell('appops', 'get', 'com.lumostech.autoclick', 'SCHEDULE_EXACT_ALARM')
import re
match = re.search(r'SCHEDULE_EXACT_ALARM: (\w+)', original_op)
op_mode = match.group(1) if match else 'default'
(OUT / f'{LABEL}-original-alarm.txt').write_text(shell('dumpsys', 'alarm'))
original_display = {key: shell('settings', '--user', '0', 'get', 'system', key)
                    for key in ('font_scale', 'accelerometer_rotation', 'user_rotation')}
original = {key: shell('settings', '--user', '0', 'get', 'secure', key)
            for key in ('enabled_accessibility_services', 'accessibility_enabled')}
(OUT / 'backup-settings.json').write_text(json.dumps(dict(settings=original, display_settings=original_display, exact_alarm_op=op_mode, exact_alarm_op_raw=original_op, exact_alarm_has_override=match is not None and op_mode != "default")))
backup = snapshot_app()
(OUT / 'backup-app.tar').write_bytes(backup)
(OUT / 'active-backup-label.txt').write_text(LABEL + '\n')
original_files = files(backup)
passed = False
try:
    shell('am', 'force-stop', 'com.lumostech.autoclick')
    backup = snapshot_app()
    (OUT / 'backup-app.tar').write_bytes(backup)
    original_files = files(backup)
    component = 'com.lumostech.autoclick/com.lumostech.accessibilitycore.AccessibilityCoreService'
    remaining = ':'.join(x for x in original['enabled_accessibility_services'].split(':')
                         if x not in ('null', '', component))
    if remaining:
        shell('settings', '--user', '0', 'put', 'secure', 'enabled_accessibility_services', remaining)
    else:
        shell('settings', '--user', '0', 'delete', 'secure', 'enabled_accessibility_services')
    shell('settings', '--user', '0', 'put', 'secure', 'accessibility_enabled', '1' if remaining else '0')
    shell('appops', 'set', 'com.lumostech.autoclick', 'SCHEDULE_EXACT_ALARM', 'allow')
    if len(sys.argv) > 3 and sys.argv[3] == 'preflight-fail':
        raise RuntimeError('Deliberate protection preflight failure')
    for apk, package in [('autoclick/build/outputs/apk/debug/autoclick-debug.apk', 'com.lumostech.autoclick'),
                         ('autoclick/build/outputs/apk/androidTest/debug/autoclick-debug-androidTest.apk', 'com.lumostech.autoclick.test')]:
        ensure_installed(apk, package)
    shell('appops', 'set', 'com.lumostech.autoclick', 'SCHEDULE_EXACT_ALARM', 'allow')
    screenshot_names = ['failure-execution', 'service-status', 'trial-result', 'quick-scheduled', 'gate-enable', 'gate-connecting', 'gate-reconnect', 'task-status', 'recovery-help', 'task-large-font', 'task-landscape', 'edit-normal', 'edit-disabled', 'edit-error', 'edit-large-font', 'edit-landscape', 'edit-result', 'edit-permission', 'edit-regranted']
    shell('rm', '-f', *['/sdcard/autoclick-' + name + '.png' for name in screenshot_names])
    command = ['shell', 'am', 'instrument', '-w', '-r', '-e', 'class', CLASSES,
               '-e', 'quickSchedule', 'true', '-e', 'uiScreenshots', 'true',
               'com.lumostech.autoclick.test/androidx.test.runner.AndroidJUnitRunner']
    if PROCESS_CHECK:
        command[-1:-1] = ['-e', 'processRecoverySeed', 'true']
    def instrument(phase=None):
        args = list(command)
        if phase: args[-1:-1] = ['-e', 'permissionPhase', phase]
        output = []
        filename = f'{LABEL}-{phase}.txt' if phase else f'{LABEL}.txt'
        with (OUT / filename).open('w') as log, (OUT / (filename + '.live-log.txt')).open('w') as live:
            monitor = subprocess.Popen(BASE + ['logcat', '-v', 'threadtime', '-s', 'AutoclickQuickCheck'], stdout=live, stderr=subprocess.STDOUT)
            try:
                process = subprocess.Popen(BASE + args, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
                for line in process.stdout:
                    print(line, end='', flush=True); output.append(line); log.write(line); log.flush()
                code = process.wait()
            finally:
                monitor.terminate()
                try:
                    monitor.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    monitor.kill(); monitor.wait(timeout=10)
        return code == 0 and 'OK (' in ''.join(output) and 'FAILURES!!!' not in ''.join(output)

    def click_text(label):
        size = list(map(int, re.findall(r'\d+', shell('wm', 'size'))))[-2:]
        for attempt in range(8):
            raw = shell('env', 'CLASSPATH=/data/local/tmp/autoclick-ui-snapshot.jar',
                        'app_process', '/system/bin', 'AutoclickUiSnapshot')
            xml_text = next(line for line in raw.splitlines() if line.startswith('<?xml'))
            (OUT / f'{LABEL}-ui-last.xml').write_text(xml_text)
            root = ET.fromstring(xml_text)
            for node in root.iter('node'):
                if node.get('text') == label and node.get('enabled') == 'true':
                    coords = list(map(int, re.findall(r'-?\d+', node.get('bounds', ''))))
                    if len(coords) == 4 and 0 <= coords[1] < coords[3] <= size[1] and 0 <= coords[0] < coords[2] <= size[0]:
                        shell('input', 'tap', str((coords[0]+coords[2])//2), str((coords[1]+coords[3])//2)); time.sleep(0.5); return
            size = list(map(int, re.findall(r'\d+', shell('wm', 'size'))))[-2:]
            shell('input', 'swipe', str(size[0]//2), str(size[1]*4//5), str(size[0]//2), str(size[1]//4), '300')
        raise AssertionError('Button unavailable: ' + label)

    if len(sys.argv) > 3 and sys.argv[3] in ('permissions', 'process'):
        call('push', str(OUT / 'shell-ui/snapshot.jar'), '/data/local/tmp/autoclick-ui-snapshot.jar')

    if len(sys.argv) > 3 and sys.argv[3] == 'permissions':
        assert instrument('seed'), 'Permission seed failed'
        # Finish instrumentation first, restore the ordinary service connection, then explicitly enable a real alarm.
        service = 'com.lumostech.autoclick/com.lumostech.accessibilitycore.AccessibilityCoreService'
        services = shell('settings', '--user', '0', 'get', 'secure', 'enabled_accessibility_services')
        without = ':'.join(x for x in services.split(':') if x not in ('null', '', service))
        if without: shell('settings', '--user', '0', 'put', 'secure', 'enabled_accessibility_services', without)
        else: shell('settings', '--user', '0', 'delete', 'secure', 'enabled_accessibility_services')
        shell('settings', '--user', '0', 'put', 'secure', 'enabled_accessibility_services', ':'.join(filter(None, [without, service])))
        shell('settings', '--user', '0', 'put', 'secure', 'accessibility_enabled', '1')
        shell('am', 'start', '-W', '-n', 'com.lumostech.autoclick/.MainActivity')
        time.sleep(1)
        click_text('停用任务'); click_text('启用任务')
        state = prefs('com.lumostech.autoclick', 'autoclick_task.xml')
        event = json.loads(state['exact_alarm'])['next']
        token, alarm_before = verify_registered_alarm(event, 'before-revoke')
        (OUT / f'{LABEL}-alarm-before-revoke.txt').write_text(alarm_before)
        pid_before = shell('pidof', 'com.lumostech.autoclick')
        assert pid_before
        shell('appops', 'set', 'com.lumostech.autoclick', 'SCHEDULE_EXACT_ALARM', 'deny')
        time.sleep(1)
        alarm_after = shell('dumpsys', 'alarm')
        assert token not in [a['token'] for a in queued_exact_alarms(alarm_after)], 'Revoked alarm remains registered'
        (OUT / f'{LABEL}-alarm-after-revoke.txt').write_text(alarm_after)
        # Revocation kills the app, leaving the old accessibility binding crashed.
        # Remove only this fixture service so the next instrumentation can bind it afresh.
        def prepare_phase_binding():
            current = shell('settings', '--user', '0', 'get', 'secure', 'enabled_accessibility_services')
            rest = ':'.join(x for x in current.split(':') if x not in ('null', '', service))
            if rest: shell('settings', '--user', '0', 'put', 'secure', 'enabled_accessibility_services', rest)
            else: shell('settings', '--user', '0', 'delete', 'secure', 'enabled_accessibility_services')
            shell('settings', '--user', '0', 'put', 'secure', 'accessibility_enabled', '1' if rest else '0')
        prepare_phase_binding()
        assert instrument('lost'), 'Permission lost stage failed'
        shell('appops', 'set', 'com.lumostech.autoclick', 'SCHEDULE_EXACT_ALARM', 'allow')
        prepare_phase_binding()
        assert instrument('granted'), 'Permission granted stage failed'
        state = prefs('com.lumostech.autoclick', 'autoclick_task.xml')
        assert not json.loads(state['task'])['enabled'] and json.loads(state['exact_alarm'])['next'] is None
        passed = True
    else:
        passed = instrument()
    if passed and PROCESS_CHECK:
        try:
            verify_process_recovery()
        except Exception as error:
            (OUT / f'{LABEL}-failure-state.txt').write_text(shell('dumpsys', 'accessibility'))
            (OUT / f'{LABEL}-failure-log.txt').write_text(shell('logcat', '-d', '-s', 'Autoclick/MyService', 'Autoclick/ClickPeriodicWorker', 'ActivityManager'))
            (OUT / f'{LABEL}-failure.txt').write_text(str(error))
            raise
    with (OUT / f'{LABEL}-diagnostics.txt').open('w') as log:
        try:
            call('logcat', '-d', '-t', '2000', '-s', 'AutoclickQuickCheck', stdout=log, timeout=15)
        except subprocess.TimeoutExpired:
            print('DIAGNOSTICS TIMEOUT: proceeding to restoration', flush=True)
    available_screenshots = set(shell('find', '/sdcard/', '-maxdepth', '1', '-name', 'autoclick-*.png').splitlines())
    for name in screenshot_names:
        path = '/sdcard/autoclick-' + name + '.png'
        if path in available_screenshots:
            call('pull', path, str(OUT / f'{LABEL}-{name}.png'))
except BaseException as error:
    import traceback
    traceback.print_exc()
    print('RESTORATION STARTED AFTER FAILURE: ' + type(error).__name__, flush=True)
    raise
finally:
    restore_errors = []
    try:
        shell('am', 'force-stop', 'com.lumostech.autoclick')
        shell('run-as', 'com.lumostech.autoclick', 'rm', '-rf', 'shared_prefs', 'databases', 'files', 'no_backup')
        call('shell', 'run-as', 'com.lumostech.autoclick', 'tar', '-xf', '-', input=backup, capture_output=True)
        restored = snapshot_app()
        assert files(restored) == original_files, 'Restored application files differ'
    except Exception as error:
        restore_errors.append(str(error))
    actions = [lambda: shell('appops', 'set', 'com.lumostech.autoclick', 'SCHEDULE_EXACT_ALARM', op_mode)]
    for namespace, values in [('system', original_display), ('secure', original)]:
        for key, value in values.items():
            args = ['settings', '--user', '0', 'delete', namespace, key] if value == 'null' else ['settings', '--user', '0', 'put', namespace, key, value]
            actions.append(lambda args=args: shell(*args))
    for action in actions:
        try:
            action()
        except Exception as error:
            restore_errors.append(str(error))
    if restore_errors:
        (OUT / f'{LABEL}-restore-errors.json').write_text(json.dumps(restore_errors, indent=2))
        raise RuntimeError('Restoration failed; private backups retained')
    if not (len(sys.argv) > 3 and sys.argv[3] == 'preflight-fail'):
        original_task_bytes = next((value for key, value in original_files.items() if key.endswith('/autoclick_task.xml')), b'<map />')
        original_pref = {e.attrib['name']: e.text if e.tag == 'string' else e.attrib.get('value') for e in ET.fromstring(original_task_bytes)}
        original_alarm = json.loads(original_pref['exact_alarm']) if original_pref.get('exact_alarm') else None
        original_config = json.loads(original_pref['task']) if original_pref.get('task') else None
        # An actual package update uses the production recovery path for an originally enabled v2 plan.
        if original_alarm and original_config and original_config['enabled']:
            call('install', '-r', 'autoclick/build/outputs/apk/debug/autoclick-debug.apk')
        shell('am', 'start', '-W', '-n', 'com.lumostech.autoclick/.MainActivity')
        time.sleep(1)
        task_before = next((value for key, value in original_files.items() if key.endswith('/autoclick_task.xml')), b'<map />')
        task_after = optional_task_xml('com.lumostech.autoclick')
        before_task, after_task = saved_task(task_before), saved_task(task_after)
        migration = original_config is not None and original_alarm is None
        if migration:
            old, new = json.loads(before_task), json.loads(after_task)
            assert not new.get('enabled'), 'Legacy task was automatically enabled'
            old['enabled'] = False
            assert old == new, 'Configuration changed during migration'
            after_prefs = prefs('com.lumostech.autoclick', 'autoclick_task.xml')
            assert json.loads(after_prefs['exact_alarm'])['status'] == 'NEEDS_ENABLE'
            for key in ['consumed_at', 'execution_record', 'closed_at']:
                original_prefs = {e.attrib['name']: e.text if e.tag == 'string' else e.attrib.get('value') for e in ET.fromstring(task_before)}
                if key in original_prefs: assert after_prefs.get(key) == original_prefs[key], key
        else:
            assert before_task == after_task, 'Saved task differs after v2 restoration'
        (OUT / f'{LABEL}-migration.json').write_text(json.dumps(dict(legacy_migrated=migration, enabled_before=original_config.get('enabled') if original_config else None, enabled_after=json.loads(after_task).get('enabled') if after_task else None), indent=2))
    restored_op = shell('appops', 'get', 'com.lumostech.autoclick', 'SCHEDULE_EXACT_ALARM')
    restored_match = re.search(r'SCHEDULE_EXACT_ALARM: (\w+)', restored_op)
    assert (restored_match.group(1) if restored_match else 'default') == op_mode
    for key, value in original.items():
        assert shell('settings', '--user', '0', 'get', 'secure', key) == value
    for key, value in original_display.items():
        assert shell('settings', '--user', '0', 'get', 'system', key) == value
    if passed or (len(sys.argv) > 3 and sys.argv[3] == 'preflight-fail'):
        (OUT / 'backup-app.tar').unlink()
        (OUT / 'backup-settings.json').unlink()
        (OUT / 'active-backup-label.txt').unlink(missing_ok=True)
    (OUT / f'{LABEL}-restore.txt').write_text('App files, saved task and accessibility settings restored and verified.\n')
    print('RESTORED: app files, saved task and accessibility settings verified.', flush=True)
sys.exit(0 if passed else 2)
