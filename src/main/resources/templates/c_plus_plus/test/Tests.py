#!/usr/bin/env python3

from tests.TestCompile import TestCompile
from tests.TestConfigure import TestConfigure
from tests.TestCatch2 import TestCatch2
from tests.TestClangFormat import TestClangFormat
from testUtils.Tester import Tester


def main() -> None:
    # Create a new instance of the tester:
    tester: Tester = Tester()

    buildDir = "./build"

    # Register all test cases:
    # Configure:
    testConfigure: TestConfigure = TestConfigure(".", buildDir,
        ["-DCMAKE_BUILD_TYPE=Debug",
         "-DCMAKE_CXX_FLAGS=-fsanitize=address",
         "-DCMAKE_EXE_LINKER_FLAGS=-fsanitize=address"])
    tester.addTest(testConfigure)
    tester.addTest(TestCompile(buildDir, "sort-test", requirements=[testConfigure.name], name="CompileSort"))
    tester.addTest(TestCatch2(buildDir, "sort-test", ["CompileSort"]))
    tester.addTest(TestClangFormat("./assignment", "src/sort.cpp", name="clang-format(sort.cpp)"))

    # Run the actual tests:
    tester.run()
    # Export the results into the JUnit XML format:
    tester.exportResult("./test-reports/tests-results.xml")


if __name__ == "__main__":
    main()
