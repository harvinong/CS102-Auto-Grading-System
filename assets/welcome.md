Welcome to the Auto-Grading System! Streamline grading by 
batch-validate and test students' submissions, and then write 
their performances in the scoresheet.

This program requires three configuration parameters:
1. Starting Folder
   - Your students modify this folder during lab test/exam.

2. Tester Files Folder
   - A folder containing your tester files.
   - One folder per question, each holding QxTester.java
     classes and the QxTester.expected.txt output each
     must print. Every matching tester earns one mark.

3. Scoresheet File
   - A csv file containing a list of your students along 
     with their grades.
   - Grading writes the marks into this file in place, so
     keep a backup copy if you need the blank sheet.