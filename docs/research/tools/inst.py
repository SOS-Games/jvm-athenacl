import csv, collections
GS_KITS = {0:'Standard',8:'Room',16:'Power',24:'Electronic',25:'TR-808/Analog',32:'Jazz',40:'Brush',48:'Orchestra',56:'SFX',127:'CM-64/MT-32'}
for l in ['generaluser-gs','fluidr3-gm','java-emergency']:
    rows = list(csv.DictReader(open(f'instruments-{l}.csv')))
    mel = [r for r in rows if r['percussion']=='false']; perc = [r for r in rows if r['percussion']=='true']
    banks = collections.Counter(int(r['java_bank']) for r in mel)
    gm = {int(r['program']) for r in mel if int(r['java_bank'])==0}
    print(f"== {l}: total {len(rows)}, melodic {len(mel)}, percussion {len(perc)}")
    print("  melodic per bank:", dict(sorted(banks.items())))
    print("  GM bank0 coverage:", len(gm), "missing:", sorted(set(range(128))-gm))
    print("  kits:", [(r['java_bank'],r['program'],r['name']) for r in perc])
    nonzero = [ (r['java_bank'],r['program'],r['name']) for r in mel if int(r['java_bank'])!=0]
    print("  sample non-zero-bank melodic:", nonzero[:12])
