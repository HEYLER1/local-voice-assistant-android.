#!/usr/bin/env python3
"""Download pinned Soniqo model bundles and build a ZIP for offline import."""
import concurrent.futures, hashlib, json, pathlib, subprocess, zipfile, argparse
p=argparse.ArgumentParser();p.add_argument('--output',required=True);args=p.parse_args()
out=pathlib.Path(args.output);cache=out.parent/'soniqo-tdt-files';cache.mkdir(parents=True,exist_ok=True)
sets={
'Parakeet-TDT-v3-ONNX':['parakeet-encoder-int8.onnx','parakeet-decoder-joint-int8.onnx','vocab.json'],
'Silero-VAD-v5-ONNX':['silero-vad.onnx'],
'DeepFilterNet3-ONNX':['deepfilter-auxiliary.bin'],
'Kokoro-82M-ONNX':['kokoro-e2e-realtime.onnx','kokoro-e2e.onnx.data','vocab_index.json','us_gold.json','us_silver.json','dict_fr.json','dict_es.json','dict_it.json','dict_pt.json','dict_hi.json','voices/af_heart.bin','voices/ff_siwis.bin','voices/ef_dora.bin','voices/if_sara.bin','voices/pf_dora.bin','voices/hf_alpha.bin','voices/jf_alpha.bin','voices/zf_xiaobei.bin']}
revisions={repo:json.loads(subprocess.check_output(['curl','-fsSL','https://huggingface.co/api/models/soniqo/'+repo]))['sha'] for repo in sets}
def fetch(item):
 repo,name=item;path=cache/name;path.parent.mkdir(parents=True,exist_ok=True)
 url=f'https://huggingface.co/soniqo/{repo}/resolve/{revisions[repo]}/{name}'
 if not path.exists():
  temporary=path.with_suffix(path.suffix+'.part');subprocess.run(['curl','-fsSL','--retry','3',url,'-o',str(temporary)],check=True);temporary.rename(path)
 digest=hashlib.sha256(path.read_bytes()).hexdigest()
 return {'file':name,'repo':'soniqo/'+repo,'revision':revisions[repo],'sha256':digest,'bytes':path.stat().st_size}
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as executor:
 rows=list(executor.map(fetch,[(repo,n) for repo,names in sets.items() for n in names]))
manifest={'sdk':'audio.soniqo:speech:0.0.22','profile':'parakeet-tdt-v3-int8','files':rows,'note':'Kokoro resources are required by the SDK pipeline constructor; V does not synthesize with them. Model licenses are separate from SDK Apache-2.0.'}
with zipfile.ZipFile(out,'w',zipfile.ZIP_STORED) as z:
 for row in rows:z.write(cache/row['file'],row['file'])
 z.writestr('model-manifest.json',json.dumps(manifest,indent=2))
out.with_suffix('.manifest.json').write_text(json.dumps(manifest,indent=2))
print('ZIP preparado:',out,'MB:',round(out.stat().st_size/1024**2,1))
