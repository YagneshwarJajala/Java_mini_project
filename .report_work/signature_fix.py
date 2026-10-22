from docx import Document
p=r'C:\Users\yagne\OneDrive\Desktop\PROJECTS\Java_mini_project\Astra_AI_One_Week_Technical_Project_Report.docx'
d=Document(p)
for x in d.paragraphs:
 if x.text.startswith('Mentor Signature:'):
  if x.runs:
   x.runs[0].text='Mentor Signature: ______    Date: ______    HoD Signature: ______'
   for r in x.runs[1:]: r.text=''
  x.paragraph_format.keep_together=False
  x.paragraph_format.keep_with_next=False
  x.paragraph_format.widow_control=False
  x.paragraph_format.space_before=0
  x.paragraph_format.space_after=0
d.save(p)
