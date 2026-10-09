import io,json,pathlib,re,subprocess,tarfile,time,xml.etree.ElementTree as ET
out=pathlib.Path(__file__).resolve().parent
base=['/Users/hgeng/Library/Android/sdk/platform-tools/adb','-s','emulator-5554']
def call(*args,**kw):return subprocess.run(base+list(args),check=True,**kw)
def shell(*args):return call('shell',*args,capture_output=True).stdout.decode().strip()
def files(data):
 with tarfile.open(fileobj=io.BytesIO(data)) as t:return {m.name:t.extractfile(m).read() for m in t.getmembers() if m.isfile()}
def values(data):return {e.attrib['name']:e.text if e.tag=='string' else e.attrib.get('value') for e in ET.fromstring(data)}
backup=(out/'backup-app.tar').read_bytes();settings=json.loads((out/'backup-settings.json').read_text());original=files(backup)
old=values(next((v for k,v in original.items() if k.endswith('/autoclick_task.xml')),b'<map />'))
shell('am','force-stop','com.lumostech.autoclick')
shell('run-as','com.lumostech.autoclick','rm','-rf','shared_prefs','databases','files','no_backup')
call('shell','run-as','com.lumostech.autoclick','tar','-xf','-',input=backup,capture_output=True)
paths=sorted({k.split('/')[0] for k in original})
actual=call('exec-out','run-as','com.lumostech.autoclick','tar','-cf','-',*paths,capture_output=True).stdout
assert files(actual)==original,'Application file restoration mismatch'
errors=[]
actions=[['appops','set','com.lumostech.autoclick','SCHEDULE_EXACT_ALARM',settings['exact_alarm_op']]]
for ns,vs in [('system',settings['display_settings']),('secure',settings['settings'])]:
 for k,v in vs.items():actions.append(['settings','--user','0',*(['delete',ns,k] if v=='null' else ['put',ns,k,v])])
for action in actions:
 try:shell(*action)
 except Exception as e:errors.append(str(e))
assert not errors,errors
config=json.loads(old['task']) if old.get('task') else None
if config and config['enabled'] and old.get('exact_alarm'):call('install','-r','autoclick/build/outputs/apk/debug/autoclick-debug.apk')
shell('am','start','-W','-n','com.lumostech.autoclick/.MainActivity')
time.sleep(1)
r=subprocess.run(base+['shell','run-as','com.lumostech.autoclick','test','-f','shared_prefs/autoclick_task.xml'],capture_output=True)
if r.returncode==1:new={}
else:
 r.check_returncode();new=values(call('exec-out','run-as','com.lumostech.autoclick','cat','shared_prefs/autoclick_task.xml',capture_output=True).stdout)
assert old.get('task')==new.get('task'),'Saved task changed'
for k in ['consumed_at','execution_record','closed_at']:assert old.get(k)==new.get(k),k
op=shell('appops','get','com.lumostech.autoclick','SCHEDULE_EXACT_ALARM');m=re.search(r'SCHEDULE_EXACT_ALARM: (\w+)',op);assert (m.group(1) if m else 'default')==settings['exact_alarm_op']
for ns,vs in [('system',settings['display_settings']),('secure',settings['settings'])]:
 for k,v in vs.items():assert shell('settings','--user','0','get',ns,k)==v,k
(out/'interrupted-ui5-restore.txt').write_text('App files, saved task and accessibility settings restored and verified after turn interruption.\n')
(out/'interrupted-ui5-recovery.json').write_text(json.dumps({'app_files_equal':True,'saved_task_equal':True,'history_and_consumption_equal':True,'settings_equal':True,'original_task_present':bool(config),'avd':'Pixel_9a_3'},indent=2)+'\n')
for n in ['backup-app.tar','backup-settings.json','active-backup-label.txt']:(out/n).unlink(missing_ok=True)
print('INTERRUPTED BATCH RESTORED AND VERIFIED; PRIVATE BACKUPS REMOVED',flush=True)
