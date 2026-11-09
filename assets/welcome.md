Welcome to the Auto-Grading System! Streamline grading by batch-
validate and test students' submissions, and then write their 
performances in the scoresheet.

This program requires three configuration parameters:
1. Starting Folder
   - Your students modify this folder during lab test/exam.

2. Tester Files Folder
   - A folder containing your tester files.
   - One folder per question, each holding QxTester.java classes 
     and the QxTester.expected.txt output each must print. Every 
     matching tester earns one mark.

3. Scoresheet File
   - A csv file containing a list of your students along with 
     their grades.
   - Grading writes the marks into this file in place, so keep a
     backup copy if you need the blank sheet.

4. Grading Timeout (seconds)
   - The maximum duration for each compile or execution process 
     for each question.
   - This prevents running the process for too long.
   - By default, it takes 2 seconds. 
   - The duration can be set up to 1 hour.

5. Output Size Limit (bytes)
   - The size limit of the standard output and error for each 
     question.
   - This prevents infinitely printing the output.
   - By default, it is 1 MiB. (1 MiB = 2^20 Bytes/characters)
   - The size limit can be set up to 64 MiB.