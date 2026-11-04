# Bounded-output demonstration

This is a trusted, finite fixture for the output-limit feature. From the repository root, run:

```sh
rm -rf demo/output-limit/target
mvn -q exec:java -Dexec.mainClass=autogradesys.App -Dexec.args="--non-interactive --submissions-dir $(pwd)/demo/output-limit/fixtures/submissions --questions-dir $(pwd)/demo/output-limit/fixtures/questions --scoresheet $(pwd)/demo/output-limit/target/scores.csv --history $(pwd)/demo/output-limit/target/history.jsonl --report $(pwd)/demo/output-limit/target/grading-report.html --timeout-ms 2000 --output-limit-bytes 128"
```

Prepare the generated scoresheet before the run:

```sh
mkdir -p demo/output-limit/target && cp demo/output-limit/fixtures/scores.csv demo/output-limit/target/scores.csv
```

Observed result: `flood/q1` receives an `output-limit` diagnostic with retained `stdout=128 bytes`, while `flood/q2` and both healthy questions pass. The process is terminated and grading continues. The HTML report, CSV and JSONL history are generated under `demo/output-limit/target/`.
