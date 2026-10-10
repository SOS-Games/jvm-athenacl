import wave, numpy as np, subprocess, re, csv, os, json
labels = ['generaluser-gs','fluidr3-gm','java-emergency']
sections = [('piano (prog 0)',0,4),('strings (prog 48)',4,8),('brass (prog 61)',8,12),('flute (prog 73)',12,16),
            ('nylon guitar (prog 24)',16,20),('bass 33 + drums',20,24),('drums solo + fill',24,28),('crash + tail',28,31)]
out = {}
for l in labels:
    w = wave.open(f'demo-{l}.wav'); sr = w.getframerate()
    x = np.frombuffer(w.readframes(w.getnframes()), dtype='<i2').reshape(-1,2).astype(np.float64)/32768
    mono = x.mean(axis=1)
    r = subprocess.run(['ffmpeg','-hide_banner','-nostats','-i',f'demo-{l}.wav','-af','ebur128=peak=true','-f','null','-'],capture_output=True,text=True).stderr
    summ = r[r.rfind('Summary:'):]
    lufs = float(re.search(r'I:\s+(-?[\d.]+) LUFS', summ).group(1)); lra = float(re.search(r'LRA:\s+([\d.]+) LU', summ).group(1))
    tp = float(re.search(r'Peak:\s+(-?[\d.]+) dBFS', summ).group(1))
    db = lambda v: 20*np.log10(max(v,1e-12))
    secs = []
    for name,a,b in sections:
        seg = mono[int(a*sr):int(b*sr)]; st = x[int(a*sr):int(b*sr)]
        rms = np.sqrt(np.mean(seg**2)); pk = np.max(np.abs(st))
        spec = np.abs(np.fft.rfft(seg*np.hanning(len(seg)))); f = np.fft.rfftfreq(len(seg),1/sr)
        cen = float((spec*f).sum()/spec.sum())
        cum = np.cumsum(spec**2); roll = float(f[np.searchsorted(cum, 0.85*cum[-1])])
        side = (st[:,0]-st[:,1])/2; mid = (st[:,0]+st[:,1])/2
        width = db(np.sqrt(np.mean(side**2))) - db(np.sqrt(np.mean(mid**2)))
        secs.append(dict(section=name, rms_dbfs=round(db(rms),1), peak_dbfs=round(db(pk),1), crest_db=round(db(pk)-db(rms),1),
                         centroid_hz=round(cen), rolloff85_hz=round(roll), side_minus_mid_db=round(width,1)))
    # decay: time from 28.0 s until 50 ms RMS window falls below -60 dBFS
    win = int(0.05*sr); t = None
    for i in range(int(28.5*sr), len(mono)-win, win):
        if db(np.sqrt(np.mean(mono[i:i+win]**2))) < -60: t = round(i/sr - 28.0, 2); break
    clip = int(np.sum(np.abs(x) >= 0.999))
    out[l] = dict(lufs=lufs, lra=lra, true_peak=tp, overall_rms_dbfs=round(db(np.sqrt(np.mean(mono**2))),1), clipped_samples=clip,
                  tail_to_minus60_s=t, sections=secs, wav_bytes=os.path.getsize(f'demo-{l}.wav'),
                  mp3_bytes=os.path.getsize(f'/workspace/research/audio/demo-{l}.mp3'))
json.dump(out, open('/workspace/research/audio/render-metrics.json','w'), indent=1)
for l,v in out.items():
    print(l, {k:v[k] for k in v if k!='sections'})
    for s in v['sections']: print('   ', s)
