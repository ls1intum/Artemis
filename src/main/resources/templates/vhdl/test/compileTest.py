from junit_xml import TestSuite, TestCase
import re
import subprocess
import argparse

#Export JUnit result
def export_junit(retCode: int, stdout: str = ""):
    if retCode != 0:
        return

    test_cases = []
    pattern = re.compile(r'\(report (note|error)\): (.*)')

    for line in stdout.split('\n'):
        match = pattern.search(line)
        if match:
            severity, message = match.groups()
            name = message.split("-")[0].strip()
            if severity == 'note':
                test_case = TestCase(name=name, stdout=message)
            else:
                test_case = TestCase(name=name)
                test_case.add_failure_info(message)
            test_cases.append(test_case)

    ts = TestSuite("ERA Tester", test_cases)
    with open('result.xml', 'w') as f:
        TestSuite.to_file(f, [ts], prettyprint=False)


makefilePath: str
makeTarget: str

#Set up argument parsing
parser = argparse.ArgumentParser(description='Calls make in the given directory, captures the output, and produces a JUnit report.')

parser.add_argument('path', metavar='path', type=str, help="Path to makefile")
parser.add_argument('--target', metavar='x', type=str, help = "Target for make", default="all")
parser.add_argument('--timeout', metavar='y', type=int, help = "Timeout for running make in seconds", default=10)

args = parser.parse_args()

#Run make with subprocess
makeArgs = ["make", "-C", args.path, args.target]
print(f"Running make with arguments: {makeArgs}\nTimeout is {args.timeout} seconds")

#Check for timeouts
try:
    res = subprocess.run(makeArgs, capture_output=True, timeout=args.timeout)
except subprocess.TimeoutExpired:
    print("Time for make exceeded!")
except OSError:
    print("OSError ecountered!")
else:
    stderr = res.stderr.decode("utf-8")
    stdout = res.stdout.decode("utf-8")
    print(f"Make exited with return code {res.returncode}.\n---stdout---\n{stdout}\n---stderr---\n{stderr}-----------")
    export_junit(res.returncode, stdout)
