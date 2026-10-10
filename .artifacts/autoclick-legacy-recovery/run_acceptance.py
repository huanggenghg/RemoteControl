"""Run protected batches serially; only summarize actual completed results."""
import json
import pathlib
import re
import subprocess

root = pathlib.Path(__file__).resolve().parent
batches = [
    ('legacy-storage-final', 'RecoverableTaskPreferencesTest,LegacyTaskRecoveryIntegrationTest,RecordingSessionIntegrationTest', None),
    ('legacy-ui-final', 'LegacyTaskRecoveryUiTest', None),
    ('legacy-regression-final', 'ScheduleEditIntegrationTest,EditScheduleUiTest,ClickTaskStatusUiTest,ClickTrialUiTest,GestureIntegrationTest,ExactTimingRecoveryTest', None),
    ('legacy-exact-final', 'ExactTimingIntegrationTest#quickExactTaskFiresAtNextMinute', None),
    *[(f'legacy-process-{phase}-final', 'LegacyRecoveryProcessTest', f'legacy-process-{phase}') for phase in ('draft', 'before', 'after')],
]
results = []
for label, classes, mode in batches:
    selectors = ','.join('com.lumostech.autoclick.' + name for name in classes.split(','))
    command = ['python3', str(root / 'run_tests.py'), label, selectors]
    if mode: command.append(mode)
    command += ['--serial', 'emulator-5554']
    print('START ' + label, flush=True)
    log = root / (label + '-console.log')
    with log.open('w') as stream:
        process = subprocess.run(command, stdout=stream, stderr=subprocess.STDOUT)
    data = log.read_text()
    count = re.search(r'OK \((\d+) tests?\)', data)
    restored = (root / (label + '-restore.txt')).exists()
    result = dict(label=label, exit_code=process.returncode, tests=int(count[1]) if count else None,
                  restored=restored, skipped=len(re.findall(r'INSTRUMENTATION_STATUS_CODE: -3', data)),
                  passed=process.returncode == 0 and bool(count) and restored and 'FAILURES!!!' not in data)
    results.append(result)
    (root / 'acceptance-batches.json').write_text(json.dumps(results, indent=2))
    print(json.dumps(result), flush=True)
    if not result['passed']: raise SystemExit(process.returncode or 2)
print('ACCEPTANCE COMPLETE', flush=True)
