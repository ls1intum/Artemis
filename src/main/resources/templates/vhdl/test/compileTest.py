from junit_xml import TestSuite, TestCase
import subprocess
import argparse

#Export JUnit result
def export_junit(retCode: int, msg: str, testName: str = 'Compile'):
    tc = TestCase(testName)

    if retCode != 0:
        tc.add_failure_info(message=msg)
    ts = TestSuite("ERA Tester", [tc])
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
    export_junit(1, f"Time for make exceeded, timeout was set at {args.timeout} seconds.")
except OSError:
    print("OSError ecountered!")
    export_junit(1, "OSError ecountered - please contact us.")
else:
    stderr = res.stderr.decode("utf-8")
    export_junit(res.returncode, f"Make exited with return code {res.returncode}.\n---stderr---\n{stderr}-----------")