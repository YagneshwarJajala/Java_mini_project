from docx import Document
from docx.oxml import OxmlElement
from docx.text.paragraph import Paragraph
from docx.enum.text import WD_ALIGN_PARAGRAPH
p=r'C:\Users\yagne\OneDrive\Desktop\PROJECTS\Java_mini_project\Astra_AI_One_Week_Technical_Project_Report.docx'
d=Document(p)
def setp(x,s):
 if x.runs:
  x.runs[0].text=s
  for r in x.runs[1:]: r.text=''
 else: x.add_run(s)
for x in d.paragraphs:
 if x.text.startswith('Implementation is organized around'):
  s=x.text.replace('\\`','').replace('`','')
  setp(x,s)
 if x.text.startswith('The JavaFX application maps its main screens'):
  s=x.text+'\n\nPassword path: [Entry field] → [Local heuristic] → [Derived counts] → [Report / estimate]\nSeparate simulation: [PROCESSING] → [100 logical agents / 8 workers] → [Synthetic candidate indexes]'
  setp(x,s)
 if x.text.strip().startswith('Figure :'):
  setp(x,'Figure : Text workflow showing the local password-analysis path and independent synthetic-index simulation.')
# Move the explanatory note before the day-wise table, so readers see it before its provisional rows.
note=next(x for x in d.paragraphs if x.text.startswith('Day-to-activity mapping below'))
tbl=d.tables[1]._tbl
note._p.addprevious(tbl)
# Keep each day row together across page breaks.
for row in d.tables[1].rows:
 trPr=row._tr.get_or_add_trPr()
 if trPr.find('{http://schemas.openxmlformats.org/wordprocessingml/2006/main}cantSplit') is None:
  trPr.append(OxmlElement('w:cantSplit'))
d.save(p)
