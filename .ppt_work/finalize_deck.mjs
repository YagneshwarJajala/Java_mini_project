import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { finalizePresentation } from 'file:///C:/Users/yagne/.codex/plugins/cache/openai-primary-runtime/presentations/26.1020.11127/skills/Presentations/container_tools/artifact_tool_utils.mjs';
const workspaceDir='C:/Users/yagne/OneDrive/Desktop/PROJECTS/Java_mini_project';
const skillDir='C:/Users/yagne/.codex/plugins/cache/openai-primary-runtime/presentations/26.1020.11127/skills/Presentations';
const candidatePath=path.join(workspaceDir,'.ppt_work/Astra_AI_Project_Presentation_draft.pptx');
const finalPath=path.join(workspaceDir,'.ppt_output/Astra_AI_Project_Presentation_10_Slides.pptx');
const result=await finalizePresentation({
  explicitTotalSlideCount:10,
  workspaceDir,candidatePath,finalPath,
  pythonExecutable:'C:/Users/yagne/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe',
  integrityValidatorPath:path.join(skillDir,'container_tools/inspect_presentation_package_integrity.py'),
  layoutValidatorPath:path.join(skillDir,'container_tools/inspect_presentation_layout_geometry.py'),
  layoutArgs:['--expected-slide-size-emu','18288000,10287000'],
  requiredNativeTableOwnerSlides:[],
  sourceTemplatePath:'C:/Users/yagne/Downloads/Java_Mini_Project_PPT_Format.pptx',
  requiredTemplateReferenceSlides:[1,2,3],
  minimumTemplateCoverageRatio:0.3,
  requireExactTemplateDimensions:true,
  fontPolicy:{basis:'reference',families:['Cooper BT Bold Italics','Canva Sans','Glacial Indifference Bold'],referencePath:'C:/Users/yagne/Downloads/Java_Mini_Project_PPT_Format.pptx',referenceSha256:'49f04b73f54a884d96b3dcc5d05b1a561c537563da9c80ed7db755c83eda9b2c'},
  verifyArtifactToolImport:true,
  receiptPath:path.join(workspaceDir,'.ppt_work/Astra_AI_Project_Presentation_10_Slides_final_validation.json'),
});
console.log(JSON.stringify(result,null,2));

