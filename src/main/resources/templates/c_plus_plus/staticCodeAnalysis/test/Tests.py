#!/usr/bin/env python3

import argparse
import sys

from tests.TestCompile import TestCompile
from tests.TestConfigure import TestConfigure
from tests.TestCatch2 import TestCatch2
from testUtils.Tester import Tester


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--only", choices=["compile", "test", "all"], default="all")
    return parser.parse_args()


def has_failures(tester: Tester) -> bool:
    return tester.suite.failures > 0 or tester.suite.errors > 0


def main() -> None:
    args = parse_args()

    # Create a new instance of the tester:
    tester: Tester = Tester()
    buildDir = "./build"

    # compile
    if args.only != "test":
        testConfigure: TestConfigure = TestConfigure(".", buildDir,
                                                     ["-DCMAKE_BUILD_TYPE=Debug",
                                                      "-DCMAKE_CXX_FLAGS=-fsanitize=address",
                                                      "-DCMAKE_EXE_LINKER_FLAGS=-fsanitize=address",
                                                      "-DCMAKE_EXPORT_COMPILE_COMMANDS=ON"])
        tester.addTest(testConfigure)
        tester.addTest(TestCompile(buildDir, "sort-test", requirements=[testConfigure.name], name="CompileSort"))

        tester.run()

    # test
    if args.only != "compile":
        tester.addTest(TestCatch2(buildDir, "sort-test"))

        tester.run()
        # Export the results into the JUnit XML format:
        tester.exportResult("./test-reports/tests-results.xml")

    sys.exit(1 if has_failures(tester) else 0)


if __name__ == "__main__":
    main()
