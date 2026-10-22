import { FileBlob, PresentationFile } from 'file:///C:/Users/yagne/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/@oai/artifact-tool/dist/artifact_tool.mjs';
const p=await PresentationFile.importPptx(await FileBlob.load('C:/Users/yagne/Downloads/Java_Mini_Project_PPT_Format.pptx'));
console.log(JSON.stringify(p.slides.items[2].shapes.items.map(s=>({id:s.id,name:s.name,geometry:s.geometry,position:s.position,text:String(s.text??'').slice(0,100)}))));
console.log('frame',JSON.stringify(p.slides.items[2].frame));
