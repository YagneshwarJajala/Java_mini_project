from pathlib import Path
import re, zipfile, os
src=Path(r'C:\Users\yagne\OneDrive\Desktop\PROJECTS\Java_mini_project\.ppt_work\Astra_AI_Project_Presentation_draft.pptx')
tmp=src.with_name(src.stem+'.patched.pptx')
pat=re.compile(rb'type="title"\s+idx="0"')
with zipfile.ZipFile(src,'r') as zin, zipfile.ZipFile(tmp,'w') as zout:
    for item in zin.infolist():
        data=zin.read(item.filename)
        if (item.filename.startswith('ppt/slides/slide') or item.filename.startswith('ppt/slideLayouts/slideLayout') or item.filename.startswith('ppt/slideMasters/slideMaster')) and item.filename.endswith('.xml'):
            data=pat.sub(b'type="title"',data)
        zout.writestr(item,data)
os.replace(tmp,src)
print('Removed exporter-added title placeholder indices from slide, layout and master parts.')
