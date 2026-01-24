#!/usr/bin/env python3

from tests.TestCompile import TestCompile
from tests.TestConfigure import TestConfigure
from tests.TestCatch2 import TestCatch2
from testUtils.Tester import Tester
import os

def main() -> None:
    # Create a new instance of the tester:
    tester: Tester = Tester()

    buildDir = "./build"

    # First make the correct CMakeLists.txt
    # Therefore, get all written tests
    test_filenames = {}
    for root, dirs, filenames in os.walk("."):
        dirs[:] = [d for d in dirs if d not in ['assignment']]

        for f in filenames:
            if f.endswith(".cpp") or f.endswith(".c"):
                name, _ = os.path.splitext(f)
                rel_path = os.path.relpath(os.path.join(root, f), ".")
                rel_path = rel_path.replace(os.sep, "/")
                test_filenames[name] = rel_path

    print(f"Found test: {test_filenames}")

    # And adjust CMakeLists.txt accordingly
    cmake_file = os.path.join('.', "CMakeLists.txt")
    if not os.path.isfile(cmake_file):
        raise FileNotFoundError(f"CMakeLists.txt not found")

    with open(cmake_file, "r", encoding="utf-8") as f:
        lines = f.readlines()

    new_lines = []
    replaced = False
    for line in lines:
        if not replaced and line.strip().startswith("add_executable"):
            new_lines.append("#AUTO_REGISTERED_TESTS" + "\n")
            replaced = True
            continue
        if not replaced:
            new_lines.append(line)

    for test_name, test_path_name in test_filenames.items():
        new_lines.append("add_executable(" + test_name + " " + test_path_name + ")\n")
        new_lines.append("target_link_libraries(" + test_name + " assignment Catch2::Catch2WithMain)\n")
        new_lines.append("add_test(NAME " + test_name + " COMMAND " + test_name + ")\n")
        new_lines.append("\n")

    with open(cmake_file, "w", encoding="utf-8") as f:
        f.writelines(new_lines)

    # Register all test cases:
    # Configure:
    testConfigure: TestConfigure = TestConfigure(".", buildDir,
                                                 ["-DCMAKE_BUILD_TYPE=Debug",
                                                  "-DCMAKE_CXX_FLAGS=-fsanitize=address",
                                                  "-DCMAKE_EXE_LINKER_FLAGS=-fsanitize=address"])
    tester.addTest(testConfigure)

    for test_name, test_path_name in test_filenames.items():
        tester.addTest(TestCompile(buildDir, test_name, requirements=[testConfigure.name], name=f"Compile_{test_name}"))
        tester.addTest(TestCatch2(buildDir, test_name, ["Compile_" + test_name]))

    # Run the actual tests:
    tester.run()
    # Export the results into the JUnit XML format:
    tester.exportResult("./test-reports/tests-results.xml")


if __name__ == "__main__":
    main()
