Welcome to the Auto-Grading System! Streamline grading by batch-
validating and testing students' submissions, and then writing their 
marks into a copy of the scoresheet.

This program asks for five settings:
1. Starting Folder
   - The starter folder your students modify during the lab test/exam
     (e.g. RenameToYourUsername, with Q1, Q2 and Q3 inside).

2. Tester Files Folder
   - The folder with the course testers (e.g. Q1aTester.java).
   - The testers can sit in one folder, or in one folder per question
     (Q1, Q2, ...). Q1aTester and Q1bTester are graded as Q1.
   - Each tester prints the student's score on its last line.

3. Scoresheet File
   - The course csv file listing your students (e.g. 
     IS442-ScoreSheet.csv), with a "Username" column.
   - The marks are saved in a new copy next to it, named with the
     date and time. The original file is never changed.

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
