import fs from 'node:fs/promises';
import crypto from 'node:crypto';
import { FileBlob, PresentationFile } from 'file:///C:/Users/yagne/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/@oai/artifact-tool/dist/artifact_tool.mjs';
const root='C:/Users/yagne/OneDrive/Desktop/PROJECTS/Java_mini_project';
const final=`${root}/.ppt_output/Astra_AI_Project_Presentation_10_Slides.pptx`;
const p=await PresentationFile.importPptx(await FileBlob.load(final));
if(p.slides.items.length!==10) throw new Error(`Slide count ${p.slides.items.length}`);
const out=`${root}/.ppt_work/final-output-renders`;
await fs.mkdir(out,{recursive:true});
const hashes=[];
for(const [i,slide] of p.slides.items.entries()){
 const png=await p.export({slide,format:'png',scale:1});
 const bytes=new Uint8Array(await png.arrayBuffer());
 await fs.writeFile(`${out}/slide-${String(i+1).padStart(2,'0')}.png`,bytes);
 if(i<3){
  const orig=await fs.readFile(`${root}/.ppt_work/originals/slide-${i+1}.png`);
  hashes.push({slide:i+1,source:crypto.createHash('sha256').update(orig).digest('hex'),final:crypto.createHash('sha256').update(bytes).digest('hex')});
 }
}
const inspection=await p.inspect({kind:'slide,textbox,shape',maxChars:20000});
await fs.writeFile(`${root}/.ppt_work/final-inspection.ndjson`,inspection.ndjson,'utf8');
console.log(JSON.stringify({slideCount:p.slides.items.length,firstThreeRenderHashes:hashes,renderedSlides:10}));
