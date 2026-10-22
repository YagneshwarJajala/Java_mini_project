import fs from 'node:fs/promises';
import { FileBlob, PresentationFile } from '@oai/artifact-tool';
const source = 'C:/Users/yagne/Downloads/Java_Mini_Project_PPT_Format.pptx';
const presentation = await PresentationFile.importPptx(await FileBlob.load(source));
console.log('slideCount', presentation.slides.items.length);
console.log('size', JSON.stringify(presentation.slideSize));
const inspection = await presentation.inspect({kind:'slide,textbox,shape,image,table,chart,notes,layout', maxChars:30000});
console.log(inspection.ndjson);
await fs.mkdir('.ppt_work/originals',{recursive:true});
for (const [i,slide] of presentation.slides.items.entries()) {
  const png = await presentation.export({slide,format:'png',scale:1});
  await fs.writeFile(`.ppt_work/originals/slide-${i+1}.png`,new Uint8Array(await png.arrayBuffer()));
}
