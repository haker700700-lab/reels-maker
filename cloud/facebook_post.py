#!/usr/bin/env python3
"""Publish generated MP4 reels to Facebook Pages using the official Graph API Reels upload flow."""
import os, json, base64, glob, time
from pathlib import Path
import requests
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
API="https://graph.facebook.com/v24.0"
def decrypt_pages():
    try:
        key=serialization.load_pem_private_key(os.environ['FB_PRIV'].encode(),None)
        raw=json.loads(os.environ.get('FB_PAGES','[]'))
        out=[]; oaep=padding.OAEP(mgf=padding.MGF1(hashes.SHA1()),algorithm=hashes.SHA1(),label=None)
        for p in raw:
            try:
                blob=base64.b64decode(p['k']); kl=key.key_size//8
                aes=key.decrypt(blob[:kl],oaep)
                token=json.loads(AESGCM(aes).decrypt(blob[kl:kl+12],blob[kl+12:],None))['token']
                out.append({'id':p['id'],'name':p.get('n',p['id']),'token':token})
            except Exception as e: print(f"⚠️ تعذر فك بيانات صفحة {p.get('n','?')}: {e}")
        return out
    except Exception as e:
        print(f"⚠️ مفتاح فك التشفير غير متاح: {e}"); return []
def publish(page, path, caption):
    size=path.stat().st_size
    r=requests.post(f"{API}/{page['id']}/video_reels",params={'upload_phase':'start','access_token':page['token']},timeout=60); r.raise_for_status(); data=r.json()
    vid=data['video_id']; upload=data['upload_url']; offset=0
    with path.open('rb') as f:
        while offset<size:
            chunk=f.read(min(4*1024*1024,size-offset))
            u=requests.post(upload,headers={'Authorization':f"OAuth {page['token']}",'offset':str(offset),'file_size':str(size),'Content-Type':'application/octet-stream'},data=chunk,timeout=180)
            u.raise_for_status(); ans=u.json(); offset=int(ans.get('end_offset',offset+len(chunk)))
    fin=requests.post(f"{API}/{page['id']}/video_reels",params={'upload_phase':'finish','video_id':vid,'video_state':'PUBLISHED','description':caption,'access_token':page['token']},timeout=90); fin.raise_for_status()
    return fin.json()
def main():
    pages=decrypt_pages(); files=sorted(Path(os.environ.get('FB_DIR','.')).glob('reel_*.mp4'))
    if not files: files=sorted(Path(os.environ.get('FB_DIR','.')).glob('*.mp4'))
    cap=os.environ.get('FB_CAPTION','').strip(); notes=[]
    if not pages: notes.append('❌ مفيش صفحات صالحة أو مفتاح فك التشفير TT_PRIV غير مضبوط.')
    for p in pages:
        ok=0
        for f in files:
            try: publish(p,f,cap); ok+=1; print(f"✅ {p['name']}: {f.name}")
            except Exception as e: print(f"❌ {p['name']} / {f.name}: {e}")
            time.sleep(2)
        notes.append(("❌ " if ok < len(files) else "✅ " ) + f"{p['name']}: {ok}/{len(files)} فيديو")
    Path('fb_result.txt').write_text('\n'.join(notes) or 'لم يتم النشر',encoding='utf-8')
if __name__=='__main__': main()
