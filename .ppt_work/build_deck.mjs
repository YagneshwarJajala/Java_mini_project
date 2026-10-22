import fs from 'node:fs/promises';
import { FileBlob, PresentationFile } from 'file:///C:/Users/yagne/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/@oai/artifact-tool/dist/artifact_tool.mjs';

const sourcePath = 'C:/Users/yagne/Downloads/Java_Mini_Project_PPT_Format.pptx';
const draftPath = 'C:/Users/yagne/OneDrive/Desktop/PROJECTS/Java_mini_project/.ppt_work/Astra_AI_Project_Presentation_draft.pptx';
const presentation = await PresentationFile.importPptx(await FileBlob.load(sourcePath));
if (presentation.slides.items.length !== 3) throw new Error(`Expected 3 source slides, got ${presentation.slides.items.length}`);
const sourceSlide = presentation.slides.items[2];
const newSlides = [];
for (let i = 0; i < 7; i++) {
  const slide = sourceSlide.duplicate();
  slide.moveTo(presentation.slides.items.length - 1);
  for (const shape of [...slide.shapes.items]) {
    if (shape.name === 'Freeform 5' || shape.name === 'TextBox 6') shape.delete();
  }
  newSlides.push(slide);
}

const BLUE = '#005AE0';
const INK = '#111111';
const MUTED = '#39465A';
const PALE = '#EEF3FF';
const LINE = '#AFC7FF';
const WHITE = '#FFFFFF';
const TITLE_FONT = 'Cooper BT Bold Italics';
const BODY_FONT = 'Canva Sans';

function text(slide, name, value, x, y, w, h, size, color = INK, options = {}) {
  const shape = slide.shapes.add({
    geometry: 'textbox', name,
    position: { left: x, top: y, width: w, height: h },
    fill: 'none', line: { style: 'solid', fill: 'none', width: 0 },
  });
  shape.text = value;
  shape.text.style = {
    typeface: options.font ?? BODY_FONT,
    fontSize: size,
    color,
    bold: options.bold ?? false,
    italic: options.italic ?? false,
    alignment: options.align ?? 'left',
    verticalAlignment: options.valign ?? 'middle',
    wrap: 'square',
    autoFit: options.autoFit ?? 'shrinkText',
    insets: 0,
    ...(options.lineSpacing ? { lineSpacing: options.lineSpacing } : {}),
  };
  return shape;
}
function box(slide, name, x, y, w, h, fill = PALE, stroke = LINE, radius = 'rounded-xl') {
  return slide.shapes.add({
    geometry: 'roundRect', name,
    position: { left: x, top: y, width: w, height: h },
    fill,
    line: { style: 'solid', fill: stroke, width: 2 },
    borderRadius: radius,
  });
}
function title(slide, value) {
  text(slide, 'slide-title', value, 78, 34, 1760, 100, 56, BLUE, {font: TITLE_FONT, bold: true, italic: true});
}
function arrow(slide, name, x, y, w = 55, h = 34) {
  return slide.shapes.add({
    geometry: 'rightArrow', name,
    position: { left: x, top: y, width: w, height: h },
    fill: BLUE, line: { style: 'solid', fill: BLUE, width: 1 },
  });
}
function number(slide, label, x, y, diameter = 64) {
  const s = slide.shapes.add({ geometry: 'ellipse', name: `step-${label}`, position: {left:x,top:y,width:diameter,height:diameter}, fill: BLUE, line: {style:'solid',fill:BLUE,width:1} });
  s.text = label;
  s.text.style = { typeface: BODY_FONT, fontSize: 28, color: WHITE, bold: true, alignment: 'center', verticalAlignment: 'middle', insets: 0 };
  return s;
}

// Minimal accuracy correction to the original slide 3 workflow strip.
{
  const s = presentation.slides.items[2];
  box(s,'slide-3-corrected-workflow',318,790,1280,154,'#FAFAFA',BLUE,'rounded-lg');
  text(s,'slide-3-path-entry','Password Input',342,798,270,42,24,BLUE,{bold:true,align:'center'});
  arrow(s,'slide-3-path-arrow-1',615,805,48,28);
  text(s,'slide-3-path-analysis','Heuristic Analysis',670,798,330,42,24,BLUE,{bold:true,align:'center'});
  arrow(s,'slide-3-path-arrow-2',1004,805,48,28);
  text(s,'slide-3-path-result','Final Result',1060,798,500,42,24,BLUE,{bold:true,align:'center'});
  text(s,'slide-3-simulation-boundary','Separate synthetic candidate-index simulation. It does not hash or crack the submitted password.\nIts throughput does not feed the educational estimate.',344,843,1228,88,21,INK,{align:'center'});
}

// Slide 4: Implementation map and repository structure.
{
  const s = newSlides[0]; title(s, 'Implementation Map: Project Folder Structure');
  text(s,'tree-root','ASTRA AI  /  Maven JavaFX project',105,140,920,58,30,BLUE,{bold:true});
  box(s,'tree-panel',90,220,975,690,'#F7F9FF',LINE);
  const tree=[
    ['pom.xml','Dependencies, Java 21 build and JavaFX launch configuration'],
    ['src/main/java/module-info.java','Java module declarations'],
    ['└─ com/astraai/Main.java','Application entry point'],
    ['   ├─ App.java · AppMetadata.java','Stage lifecycle and window metadata'],
    ['   ├─ ui/','Screens, password analysis, estimates and report parsing'],
    ['   └─ simulation/','Synthetic tasks, agents, events and validation'],
    ['src/test/java/com/astraai/','JUnit tests organized by ui/ and simulation/'],
  ];
  tree.forEach((r,i)=>{
    const y=260+i*82;
    text(s,`tree-name-${i}`,r[0],130,y,420,46,i===0||i===1||i===6?24:22,i===0||i===4||i===5?BLUE:INK,{bold:true});
    text(s,`tree-desc-${i}`,r[1],560,y,465,50,20,MUTED);
    if(i<tree.length-1) box(s,`tree-rule-${i}`,130,y+60,865,2,'#DCE6FA','#DCE6FA','rounded-sm');
  });
  box(s,'module-card',1110,220,710,292,'#E4EDFF',LINE);
  text(s,'module-title','Two implementation packages',1150,248,620,56,30,BLUE,{bold:true,align:'center'});
  box(s,'module-ui',1160,330,260,120,WHITE,LINE); text(s,'module-ui-t','ui',1170,345,240,40,29,BLUE,{bold:true,align:'center'}); text(s,'module-ui-d','JavaFX views + domain rules',1170,390,240,42,19,MUTED,{align:'center'});
  box(s,'module-sim',1500,330,260,120,WHITE,LINE); text(s,'module-sim-t','simulation',1510,345,240,40,25,BLUE,{bold:true,align:'center'}); text(s,'module-sim-d','Bounded synthetic execution',1510,390,240,42,19,MUTED,{align:'center'});
  box(s,'test-card',1110,560,710,350,PALE,LINE);
  text(s,'test-title','Tests mirror the package layout',1150,590,620,54,28,BLUE,{bold:true,align:'center'});
  text(s,'test-ui','ui tests  •  input policy  •  estimator  •  KDF reports  •  screen formatting',1160,680,600,90,23,INK,{align:'center'});
  text(s,'test-sim','simulation tests  •  partitioning  •  engine lifecycle  •  result validation',1160,795,600,75,23,INK,{align:'center'});
}

// Slide 5: JavaFX application and class structure.
{
  const s = newSlides[1]; title(s, 'Startup and Screen Classes');
  const nodes=[['Main','main(args)','Calls Application.launch(App.class, args)'],['App','start(Stage)\nstop()','Creates Scene and Stage; closes view on shutdown'],['AstraApplicationView','submitPassword()\nshow(state)','Coordinates screens and simulation lifecycle']];
  const xs=[100,690,1280];
  nodes.forEach((n,i)=>{box(s,`startup-${i}`,xs[i],270,500,230,i===2?'#E4EDFF':PALE,LINE);text(s,`startup-name-${i}`,n[0],xs[i]+25,295,450,48,30,BLUE,{bold:true,align:'center'});text(s,`startup-method-${i}`,n[1],xs[i]+30,360,440,70,24,INK,{bold:true,align:'center'});text(s,`startup-desc-${i}`,n[2],xs[i]+35,438,430,50,19,MUTED,{align:'center'});if(i<2)arrow(s,`startup-arrow-${i}`,xs[i]+520,365,55,35);});
  text(s,'fx-call-title','JavaFX lifecycle calls',150,585,650,50,27,BLUE,{bold:true});
  box(s,'fx-code',120,650,790,235,'#111827','#111827');
  text(s,'fx-code-text','Application.launch(App.class, args);\nnew Scene(applicationView, 1280, 900);\nstage.setScene(scene);  stage.show();\nPlatform.runLater(() -> processing.setStatus("Engine ready"));',155,685,715,170,20,WHITE,{font:BODY_FONT});
  box(s,'screen-state-card',1000,600,780,285,'#E4EDFF',LINE);
  text(s,'screen-state-title','Screen classes',1040,630,700,46,28,BLUE,{bold:true,align:'center'});
  text(s,'screen-state-list','PasswordEntryView  →  InitializationView  →  PasswordSecurityReportView',1055,705,670,75,24,INK,{bold:true,align:'center'});
  text(s,'screen-state-caption','AstraApplicationView switches among the three presentation states.',1055,800,670,52,21,MUTED,{align:'center'});
}

// Slide 6: Input and heuristic analysis classes.
{
  const s = newSlides[2]; title(s, 'Password Input and Heuristic Classes');
  const steps=[['PasswordEntryView','PasswordField + visible TextField'],['PasswordInputPolicy','analyzeAndErase(char[])'],['PasswordAnalysis','length + class counts + patterns'],['PasswordSecurityGuidance','from(analysis) → recommendations']];
  const xs=[70,520,970,1420];
  steps.forEach((n,i)=>{box(s,`input-step-${i}`,xs[i],325,405,235,i===2?'#E4EDFF':PALE);text(s,`input-name-${i}`,n[0],xs[i]+18,355,369,60,25,BLUE,{bold:true,align:'center'});text(s,`input-method-${i}`,n[1],xs[i]+25,435,355,75,21,INK,{font:BODY_FONT,align:'center'});if(i<3)arrow(s,`input-arrow-${i}`,xs[i]+415,420,32,36);});
  box(s,'input-code',110,650,860,220,'#111827','#111827');
  text(s,'input-code-label','IMPLEMENTATION DETAIL',145,672,780,40,20,'#8BB4FF',{bold:true});
  text(s,'input-code-text','PasswordInputPolicy.analyzeAndErase(input)\nCharacter.isUpperCase(c)\nCharacter.isLowerCase(c)\nCharacter.isDigit(c)',145,725,790,120,22,WHITE,{font:BODY_FONT});
  box(s,'input-boundary',1040,650,760,220,'#E4EDFF',LINE);
  text(s,'input-boundary-title','What the heuristic records',1080,680,680,45,26,BLUE,{bold:true,align:'center'});
  text(s,'input-boundary-copy','Character counts and predictable patterns. It does not query breach databases, reuse history or dictionary membership.',1090,750,660,90,23,INK,{align:'center'});
}

// Slide 7: Synthetic simulation implementation.
{
  const s = newSlides[3]; title(s, 'Synthetic Simulation: Classes and Data Flow');
  const nodes=[['SimulationConfig','bounds + worker settings'],['TaskPartitioner','partition(config)'],['SimulationEngine','commands + state + events'],['AgentCoordinator','assigns synthetic ranges'],['ResultValidator','checks completion evidence']];
  const xs=[75,440,805,1170,1535];
  nodes.forEach((n,i)=>{box(s,`sim-${i}`,xs[i],325,320,220,i===2?'#E4EDFF':PALE);text(s,`sim-name-${i}`,n[0],xs[i]+12,355,296,58,22,BLUE,{bold:true,align:'center'});text(s,`sim-desc-${i}`,n[1],xs[i]+20,435,280,70,19,INK,{align:'center'});if(i<4)arrow(s,`sim-arrow-${i}`,xs[i]+325,415,34,35);});
  box(s,'partition-diagram',130,630,790,250,'#F7F9FF',LINE);
  text(s,'partition-title','TaskPartitioner.partition(config)',165,655,720,45,24,BLUE,{bold:true,align:'center'});
  const segX=[180,385,590,795];
  segX.forEach((x,i)=>{box(s,`range-${i}`,x,745,155,65,['#CFE0FF','#9FC0FF','#CFE0FF','#9FC0FF'][i],BLUE,'rounded-md');text(s,`range-label-${i}`,`range ${i+1}`,x,755,155,40,18,INK,{bold:true,align:'center'});});
  text(s,'range-check','Contiguous ranges cover the configured candidate index space.',180,825,690,38,18,MUTED,{align:'center'});
  box(s,'simulation-boundary',1010,630,780,250,'#E4EDFF',LINE);
  text(s,'simulation-boundary-title','Data boundary',1050,660,700,45,27,BLUE,{bold:true,align:'center'});
  text(s,'simulation-boundary-copy','SyntheticChallenge evaluates generated indexes. The submitted password and its heuristic result are not sent to the engine.',1070,730,660,100,24,INK,{align:'center'});
}

// Slide 8: Estimation, benchmark and report classes.
{
  const s = newSlides[4]; title(s, 'Estimator and Optional KDF Benchmark');
  box(s,'estimate-panel',85,205,825,600,PALE,LINE);
  text(s,'estimate-title','Educational estimate path',120,230,755,54,30,BLUE,{bold:true,align:'center'});
  const est=[['CrackTimeAssumptionDataset','select supported profile'],['CrackTimeEstimator.estimate(...)','model candidate space / rate'],['CrackTimeEstimate','ESTIMATED or unavailable']];
  est.forEach((n,i)=>{const y=335+i*130;box(s,`est-box-${i}`,160,y,675,96,i===1?'#E4EDFF':WHITE);text(s,`est-name-${i}`,n[0],180,y+10,635,35,21,BLUE,{bold:true,align:'center'});text(s,`est-detail-${i}`,n[1],180,y+50,635,32,18,MUTED,{align:'center'});if(i<2)arrow(s,`est-arrow-${i}`,465,y+98,56,26);});
  text(s,'est-note','Hypothetical project rates drive the model. Simulation throughput is separate.',145,750,705,48,20,INK,{align:'center'});
  box(s,'benchmark-panel',1010,205,825,600,'#E4EDFF',LINE);
  text(s,'benchmark-title','Explicit local verification benchmark',1045,230,755,54,28,BLUE,{bold:true,align:'center'});
  const bm=[['KdfVerificationBenchmark','bounded warmups + samples'],['KdfBenchmarkReport','completed run metadata'],['KdfBenchmarkReportLoader','loads selected path + validates']];
  bm.forEach((n,i)=>{const y=335+i*130;box(s,`bm-box-${i}`,1085,y,675,96,WHITE);text(s,`bm-name-${i}`,n[0],1100,y+10,645,35,21,BLUE,{bold:true,align:'center'});text(s,`bm-detail-${i}`,n[1],1100,y+50,645,32,18,MUTED,{align:'center'});if(i<2)arrow(s,`bm-arrow-${i}`,1395,y+98,56,26);});
  text(s,'benchmark-note','Random fixture only. Verification latency is not crack throughput. Report metadata matching does not authenticate the file.',1060,750,730,52,19,INK,{align:'center'});
}

// Slide 9: Predefined Java and library APIs used.
{
  const s = newSlides[5]; title(s, 'Predefined APIs Used in the Implementation');
  const cards=[
    ['JavaFX','Application.launch(...)\nStage.setScene(...)\nPlatform.runLater(...)','Launch app, compose window, update UI thread'],
    ['Java concurrency','Executors.newSingleThreadExecutor(...)\nTimeUnit.MILLISECONDS\nAtomicLong / AtomicReference','Run commands off UI thread; coordinate snapshots'],
    ['Java security / crypto','SecureRandom\nMessageDigest\nSecretKeyFactory + PBEKeySpec','Generate random fixture and execute supported KDF work'],
    ['Java NIO','Path.toAbsolutePath().normalize()\nFiles.isRegularFile(...)\nFiles.newInputStream(...)','Read only user-selected benchmark report path'],
  ];
  const coords=[[90,220],[985,220],[90,555],[985,555]];
  cards.forEach((c,i)=>{const [x,y]=coords[i];box(s,`api-card-${i}`,x,y,820,285,i%2===0?PALE:'#E4EDFF',LINE);text(s,`api-title-${i}`,c[0],x+28,y+20,764,48,28,BLUE,{bold:true});text(s,`api-methods-${i}`,c[1],x+35,y+92,450,155,19,INK,{font:BODY_FONT});text(s,`api-role-${i}`,c[2],x+505,y+88,275,160,20,MUTED,{align:'center'});});
  text(s,'api-deps','External library: Bouncy Castle provides Argon2, bcrypt and scrypt implementations. Maven manages dependencies and the JavaFX launch plugin.',135,890,1650,55,20,BLUE,{bold:true,align:'center'});
}

// Slide 10: Build, tests and implementation summary.
{
  const s = newSlides[6]; title(s, 'Build and Test Evidence');
  const flow=[['pom.xml','Java 21 + JavaFX + JUnit'],['mvn test','Unit and boundary tests'],['mvn package','Build artifact']];
  const xs=[150,760,1370];
  flow.forEach((n,i)=>{box(s,`build-${i}`,xs[i],255,400,190,i===1?'#E4EDFF':PALE);text(s,`build-title-${i}`,n[0],xs[i]+22,285,356,52,30,BLUE,{bold:true,align:'center'});text(s,`build-copy-${i}`,n[1],xs[i]+35,360,330,55,22,INK,{align:'center'});if(i<2)arrow(s,`build-arrow-${i}`,xs[i]+415,325,150,38);});
  box(s,'test-suite',115,535,800,320,'#F7F9FF',LINE);
  text(s,'test-suite-title','JUnit coverage by package',155,565,720,48,28,BLUE,{bold:true,align:'center'});
  text(s,'test-suite-ui','ui  •  input policy  •  estimates  •  KDF benchmark reports  •  view state',170,650,690,60,22,INK,{align:'center'});
  text(s,'test-suite-sim','simulation  •  task partitioning  •  engine events  •  result validation',170,750,690,60,22,INK,{align:'center'});
  box(s,'test-count',1010,535,790,320,'#E4EDFF',LINE);
  text(s,'test-count-big','134',1050,580,710,105,70,BLUE,{bold:true,align:'center'});
  text(s,'test-count-copy','tests passed in Maven validation',1060,695,690,55,27,INK,{bold:true,align:'center'});
  text(s,'test-commands','mvn test   ✓\nmvn package   ✓',1090,765,630,70,23,BLUE,{bold:true,align:'center'});
  text(s,'closing-line','Implementation separates UI, analysis rules, estimates and synthetic execution.',170,900,1580,56,25,BLUE,{bold:true,align:'center'});
}

if (presentation.slides.items.length !== 10) throw new Error(`Expected 10 slides after authoring, got ${presentation.slides.items.length}`);
await fs.mkdir('C:/Users/yagne/OneDrive/Desktop/PROJECTS/Java_mini_project/.ppt_work/final-renders',{recursive:true});
for (const [i,slide] of presentation.slides.items.entries()) {
  const png=await presentation.export({slide,format:'png',scale:1});
  await fs.writeFile(`C:/Users/yagne/OneDrive/Desktop/PROJECTS/Java_mini_project/.ppt_work/final-renders/slide-${String(i+1).padStart(2,'0')}.png`,new Uint8Array(await png.arrayBuffer()));
}
const pptx=await PresentationFile.exportPptx(presentation);
await pptx.save(draftPath);
console.log(JSON.stringify({slideCount:presentation.slides.items.length,draftPath}));


