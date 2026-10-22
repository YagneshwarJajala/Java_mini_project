import { FileBlob, PresentationFile } from 'file:///C:/Users/yagne/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/@oai/artifact-tool/dist/artifact_tool.mjs';
const p=await PresentationFile.importPptx(await FileBlob.load('C:/Users/yagne/Downloads/Java_Mini_Project_PPT_Format.pptx'));
for(const [n,x] of [['shape',p.slides.items[2].shapes.items[3]],['shapecoll',p.slides.items[2].shapes],['slidecoll',p.slides],['slide',p.slides.items[2]]]) console.log(n,Object.getOwnPropertyNames(Object.getPrototypeOf(x)).join(','));
