from docx import Document
from lxml import etree
for f in [r'C:\Users\yagne\Downloads\Project_Report_Format_Java_Mini_Project.docx',r'C:\Users\yagne\OneDrive\Desktop\PROJECTS\Java_mini_project\Astra_AI_One_Week_Technical_Project_Report.docx']:
 d=Document(f); p=next(x for x in d.paragraphs if 'HoD SIGNATURE' in x.text)
 print(f, etree.tostring(p._p,encoding='unicode'))
