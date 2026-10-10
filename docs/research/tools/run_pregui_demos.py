import sys, os, glob, hashlib, importlib.util, io, contextlib
sys.path.insert(0, '/workspace/pregui')
from athenaCL.libATH import athenaObj
res = []
for fp in sorted(glob.glob('/workspace/pregui/athenaCL/demo/midi/*.py')):
    name = os.path.basename(fp)[:-3]
    spec = importlib.util.spec_from_file_location(name, fp); m = importlib.util.module_from_spec(spec); spec.loader.exec_module(m)
    ath = athenaObj.Interpreter(); 
    buf = io.StringIO()
    try:
        with contextlib.redirect_stdout(buf):
            ath.cmd('apr off')
            for l in ['tpsd 7', 'tmsd 7'] + m.cmd: ath.cmd(l)
            ath.cmd('eln /tmp/goldens/%s.xml' % name)
        mid = '/tmp/goldens/%s.mid' % name
        h = hashlib.sha256(open(mid,'rb').read()).hexdigest()[:16] if os.path.exists(mid) else 'NO-MIDI'
        res.append((name, h, os.path.getsize(mid) if os.path.exists(mid) else 0))
    except Exception as e:
        res.append((name, 'ERROR ' + repr(e)[:80], 0))
for r in res: print(*r)
