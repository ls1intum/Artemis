from testUtils.Tester import Tester
from tests.TestCompile import TestCompile
from tests.TestASan import TestASan
from tests.TestUBSan import TestUBSan
from tests.TestLSan import TestLSan
from tests.TestOutput import TestOutput
from random import seed, choice


def main():
    # Makefile location:
    # Artemis expects it to be located in ../assignment
    makefileLocation: str = "../assignment"

    # Create a new instace of the tester:
    tester: Tester = Tester()

    # Register all test cases:

    # Basic compile test:
    # Run after the sanitizer so we run the tests without any sanitizer enabled
    testCompile: TestCompile = TestCompile(makefileLocation, "hexdump")
    tester.addTest(testCompile)

    # Test Output:
    # This test requires the "TestCompile" to finish with "SUCCESS" to be run:
    with open("/tmp/hello_world.txt", "w", encoding='utf-8') as f:
        f.write("Hello World!")
    with open("/tmp/lazy_fox.txt", "w", encoding='utf-8') as f:
        f.write("The quick brown fox jumps over the lazy dog")
    with open("/tmp/numbers.txt", "w", encoding='utf-8') as f:
        for i in range(100):
            f.write("{} ".format(i))
    with open("/tmp/random_fox.txt", "w", encoding='utf-8') as f:
        seed()
        for i in range(100):
            f.write("{:s} ".format(
                choice(
                    ["The", "quick", "brown", "fox", "jumps", "over", "the", "lazy", "dog", "+", "-", "~", "0", "!!!",
                     ".", ",", " ", ";"])))
    with open("/tmp/dev_random_short.txt", "wb") as f:
        with open("/tmp/dev_random_medium.txt", "wb") as f2:
            with open("/tmp/dev_random_long.txt", "wb") as f3:
                with open("/dev/urandom", "rb") as rand:
                    f.write(rand.read(16))
                    f2.write(rand.read(84))
                    f3.write(rand.read(444))

    tester.addTest(TestOutput(makefileLocation, [testCompile.name], files=[
                   "/tmp/hello_world.txt"], name="HelloWorld"))
    tester.addTest(TestOutput(makefileLocation, [testCompile.name], files=["/tmp/lazy_fox.txt", "/tmp/numbers.txt"],
                              name="LazyFoxNumbers"))

    tester.addTest(TestOutput(makefileLocation, [
                   testCompile.name], files=[], name="NoInput"))
    tester.addTest(TestOutput(makefileLocation, [testCompile.name], files=[
                   "/tmp/random_fox.txt"], name="RandomFox"))

    tester.addTest(
        TestOutput(makefileLocation, [testCompile.name], files=["/tmp/dev_random_short.txt"], name="DevRandomShort"))
    tester.addTest(TestOutput(makefileLocation, [testCompile.name], files=["/tmp/dev_random_medium.txt"],
                              name="DevRandomMedium"))
    tester.addTest(
        TestOutput(makefileLocation, [testCompile.name], files=["/tmp/dev_random_long.txt"], name="DevRandomLong"))

    # Sanitizer:

    # Address Sanitizer:
    # This test requires the "TestCompile" to finish with "SUCCESS" to be run:
    testASan: TestASan = TestASan(
        makefileLocation, requirements=[testCompile.name])
    tester.addTest(testASan)
    # This test requires the "TestASan" to finish with "SUCCESS" to be run:
    tester.addTest(
        TestOutput(makefileLocation, [testCompile.name], files=["/tmp/hello_world.txt"], name="HelloWorldASan",
                   executable="asan.out"))
    tester.addTest(
        TestOutput(makefileLocation, [testCompile.name], files=["/tmp/dev_random_long.txt"], name="DevRandomLongASan",
                   executable="asan.out"))

    # Undefined Behavior Sanitizer:
    # This test requires the "TestCompile" to finish with "SUCCESS" to be run:
    testUBSan: TestUBSan = TestUBSan(
        makefileLocation, requirements=[testCompile.name])
    tester.addTest(testUBSan)
    # This test requires the "TestUBSan" to finish with "SUCCESS" to be run:
    tester.addTest(
        TestOutput(makefileLocation, [testCompile.name], files=["/tmp/hello_world.txt"], name="HelloWorldUBSan",
                   executable="ubsan.out"))
    tester.addTest(
        TestOutput(makefileLocation, [testCompile.name], files=["/tmp/dev_random_long.txt"], name="DevRandomLongUBSan",
                   executable="ubsan.out"))

    # Leak Sanitizer:
    # This test requires the "TestCompile" to finish with "SUCCESS" to be run:
    testLSan: TestLSan = TestLSan(
        makefileLocation, requirements=[testCompile.name])
    tester.addTest(testLSan)
    # This test requires the "TestLSan" to finish with "SUCCESS" to be run:
    tester.addTest(
        TestOutput(makefileLocation, [testCompile.name], files=["/tmp/hello_world.txt"], name="HelloWorldLSan",
                   executable="lsan.out"))
    tester.addTest(
        TestOutput(makefileLocation, [testCompile.name], files=["/tmp/dev_random_long.txt"], name="DevRandomLongLSan",
                   executable="lsan.out"))

    # Run the actual tests:
    tester.run()
    # Export the results into the JUnit XML format:
    tester.exportResult("../test-reports/tests-results.xml")


if __name__ == '__main__':
    main()
