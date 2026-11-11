Welcome to the Auto-Grading System! It checks and fixes students'
submissions, runs the instructor's tester files on them, and writes
their marks into the scoresheet.

The first time you run it, it asks for five settings:
1. Starting Folder
   - The blank template the students started from, such as
     RenameToYourUsername (with its Q1, Q2 and Q3 folders).

2. Tester Files Folder
   - The folder of the instructor's testers, such as Q1aTester.java.
   - Loose testers are matched to questions by name: Q1aTester.java
     and Q1bTester.java grade Q1, Q3Tester.java grades Q3.

3. Scoresheet File
   - The .csv exported from eLearn, with a Username column. Each
     student's total goes into "Calculated Final Grade Numerator".
   - Grading writes the marks into this file in place, so keep a
     backup copy if you need the blank sheet.

4. Grading Timeout (seconds)
   - The longest each compile or tester run may take, so an
     infinite loop cannot stop grading. Default 2, at most 3600.

5. Output Size Limit (bytes)
   - The most output each run may print. Default 1 MiB
     (1048576 bytes), at most 64 MiB.

Press Enter at a prompt to keep the value shown in brackets.
