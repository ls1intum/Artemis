package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.buildagent.dto.LocalCITestJobDTO;
import de.tum.cit.aet.artemis.buildagent.service.parser.TestResultXmlParser;

class TestResultXmlParserTest {

    private final List<LocalCITestJobDTO> failedTests = new ArrayList<>();

    private final List<LocalCITestJobDTO> successfulTests = new ArrayList<>();

    @Test
    void testParseResultXmlInnerText() throws IOException {
        String exampleXml = """
                <testsuite>
                    <testcase name="testBubbleSort()" classname="testpackage.SortingExampleBehaviorTest" time="0.000306">
                         <failure>test `add` failed on ≥ 1 cases:
                (0, 0)
                Your submission raised an error Failure(&quot;TODO add&quot;)</failure>
                     </testcase>
                 </testsuite>
                """;

        TestResultXmlParser.processTestResultFile(exampleXml, failedTests, successfulTests);
        assertThat(failedTests).hasSize(1);
        var test = failedTests.getFirst();
        assertThat(test.name()).isEqualTo("testBubbleSort()");
        assertThat(test.testMessages()).containsExactly("""
                test `add` failed on ≥ 1 cases:
                (0, 0)
                Your submission raised an error Failure("TODO add")""");

    }

    @Test
    void testParseResultXmlMessageAttribute() throws IOException {
        String exampleXml = """
                <testsuite>
                    <testcase name="testBubbleSort()" classname="testpackage.SortingExampleBehaviorTest" time="0.000306">
                        <failure message = "test `add` failed"/>
                    </testcase>
                </testsuite>
                """;

        TestResultXmlParser.processTestResultFile(exampleXml, failedTests, successfulTests);
        assertThat(failedTests).hasSize(1);
        var test = failedTests.getFirst();
        assertThat(test.name()).isEqualTo("testBubbleSort()");
        assertThat(test.testMessages()).containsExactly("test `add` failed");
    }

    @Test
    void testParseResultXmlCData() throws IOException {
        String exampleXml = """
                 <testsuite>
                    <testcase name="testMergeSort()" classname="testpackage.SortingExampleBehaviorTest" time="0.059">
                        <failure type="org.opentest4j.AssertionFailedError"><![CDATA[org.opentest4j.AssertionFailedError: Deine Einreichung enthält keine Ausgabe. (67cac2)]]></failure>
                    </testcase>
                 </testsuite>
                """;

        TestResultXmlParser.processTestResultFile(exampleXml, failedTests, successfulTests);
        assertThat(failedTests).hasSize(1);
        var test = failedTests.getFirst();
        assertThat(test.name()).isEqualTo("testMergeSort()");
        assertThat(failedTests.getFirst().testMessages()).containsExactly("org.opentest4j.AssertionFailedError: Deine Einreichung enthält keine Ausgabe. (67cac2)");
    }

    @Test
    void testSuccessfulTests() throws IOException {
        String exampleXml = """
                <testsuite>
                  <properties/>
                  <testcase name="testMergeSort()" classname="testpackage.SortingExampleBehaviorTest" time="0.029"/>
                  <testcase name="testUseBubbleSortForSmallList()" classname="testpackage.SortingExampleBehaviorTest" time="0.029"/>
                  <testcase name="testBubbleSort()" classname="testpackage.SortingExampleBehaviorTest" time="0.026"/>
                  <testcase name="testUseMergeSortForBigList()" classname="testpackage.SortingExampleBehaviorTest" time="0.027"/>
                </testsuite>
                """;

        TestResultXmlParser.processTestResultFile(exampleXml, failedTests, successfulTests);
        assertThat(failedTests).isEmpty();
        assertThat(successfulTests).hasSize(4);
        assertThat(successfulTests).map(LocalCITestJobDTO::name).containsExactlyInAnyOrder("testMergeSort()", "testUseBubbleSortForSmallList()", "testBubbleSort()",
                "testUseMergeSortForBigList()");
    }

    @Test
    void testWithTestSuitesWrapper() throws Exception {
        String input = """
                <?xml version='1.0' encoding='utf-8'?>
                <testsuites>
                  <testsuite
                      id='0'
                      package='course_avg_grade [hidden]'
                      name='course_avg_grade [hidden]'
                      timestamp='2024-05-18T15:16:31'
                      hostname='e4e8695d4525'
                      tests='12'
                      failures='0'
                      errors='0'
                      time='7.964748'>
                    <properties>
                      <property name='suite_name' value='anon' />
                    </properties>
                    <testcase name='0:remove_by_id' classname='0:remove_by_id' time='0.000754'>
                    </testcase>
                    <testcase name='1:remove_by_id [hidden]' classname='1:remove_by_id [hidden]' time='7.548552'>
                    </testcase>
                    <testcase name='2:count_in_semester' classname='2:count_in_semester' time='0.001188'>
                    </testcase>
                    <testcase name='3:count_in_semester [hidden]' classname='3:count_in_semester [hidden]' time='7.581734'>
                    </testcase>
                    <testcase name='4:student_avg_grade' classname='4:student_avg_grade' time='0.000529'>
                    </testcase>
                    <testcase name='5:student_avg_grade [hidden]' classname='5:student_avg_grade [hidden]' time='7.589047'>
                    </testcase>
                    <testcase name='6:course_avg_grade' classname='6:course_avg_grade' time='0.000501'>
                    </testcase>
                    <testcase name='7:course_avg_grade [hidden]' classname='7:course_avg_grade [hidden]' time='7.861614'>
                    </testcase>
                    <testcase name='8:remove_by_id' classname='8:remove_by_id' time='0.000438'>
                    </testcase>
                    <testcase name='9:count_in_semester' classname='9:count_in_semester' time='0.000433'>
                    </testcase>
                    <testcase name='10:student_avg_grade' classname='10:student_avg_grade' time='0.000414'>
                    </testcase>
                    <testcase name='11:course_avg_grade' classname='11:course_avg_grade' time='0.000377'>
                    </testcase>
                    <system-err />
                  </testsuite>
                </testsuites>
                """;

        TestResultXmlParser.processTestResultFile(input, failedTests, successfulTests);
        assertThat(failedTests).isEmpty();
        assertThat(successfulTests).isNotEmpty().hasSize(12);
    }

    @Test
    void testSkippedTest() throws IOException {
        String input = """
                <testsuite>
                    <testcase name="testBubbleSort()" classname="testpackage.SortingExampleBehaviorTest" time="0.000306">
                        <skipped message="This test was skipped."/>
                    </testcase>
                </testsuite>
                """;

        TestResultXmlParser.processTestResultFile(input, failedTests, successfulTests);
        assertThat(failedTests).isEmpty();
        assertThat(successfulTests).isEmpty();
    }

    @Test
    void testOutputInvalidXMLCharacters() throws IOException {
        String input = """
                <?xml version='1.0' encoding='us-ascii'?>
                <testsuites>
                <testsuite name="GBS-Tester-1.36">
                <testcase name="CompileLinkedList" time="1.90495">
                <failure message="Build for directory ../assignment/build failed. Returncode is 2.">Build for directory ../assignment/build failed. Returncode is 2.
                ======================stdout======================
                [ 50%] [32mBuilding CXX object CMakeFiles/linked-list-iterator-test.dir/linked-list-iterator-test.cpp.o[0m


                ======================stderr======================
                /var/tmp/testing-dir/tests/linked-list-iterator-test.cpp:52:5: error: &#8216;linked_list&#8217; was not declared in this scope
                   52 |     linked_list&lt;TestType&gt; list;
                      |     ^~~~~~~~~~~
                /var/tmp/testing-dir/tests/linked-list-iterator-test.cpp:52:25: error: expected primary-expression before &#8216;&gt;&#8217; token
                   52 |     linked_list&lt;TestType&gt; list;
                      |                         ^
                </failure></testcase></testsuite></testsuites>
                """;
        TestResultXmlParser.processTestResultFile(input, failedTests, successfulTests);
        assertThat(successfulTests).isEmpty();
        assertThat(failedTests).hasSize(1);
        var test = failedTests.getFirst();
        assertThat(test.name()).isEqualTo("CompileLinkedList");
        assertThat(test.testMessages().getFirst()).isEqualTo("Build for directory ../assignment/build failed. Returncode is 2.");
    }

    @Test
    void testEmptyTestMessage() throws IOException {
        String input = """
                <testsuites>
                    <testsuite package="mwe-package" id="0" name="mwe-suite-name" timestamp="2024-08-09T12:34:56"
                    hostname="localhost" tests="1" failures="1" errors="0" time="0">
                        <properties></properties>
                            <testcase name="mwe-name" classname="mwe-class" time="0">
                                <failure type="empty"></failure>
                        </testcase>
                    </testsuite>
                </testsuites>
                """;

        TestResultXmlParser.processTestResultFile(input, failedTests, successfulTests);
        assertThat(failedTests).hasSize(1);
        var test = failedTests.getFirst();
        assertThat(test.name()).isEqualTo("mwe-name");
        assertThat(test.testMessages()).hasSize(1).contains("");
    }

    @Test
    void testNestedTestsuite() throws IOException {
        String input = """
                <?xml version="1.0" encoding="UTF-8" standalone="no"?>
                <testsuites errors="0" failures="0" tests="12" time="0.013">
                    <testsuite name="Tests" tests="12">
                        <testsuite name="Properties" tests="9">
                            <testsuite name="Checked by SmallCheck" tests="6">
                                <testcase classname="Tests.Properties.Checked by SmallCheck" name="Testing filtering in A" time="0.004"/>
                                <testcase classname="Tests.Properties.Checked by SmallCheck" name="Testing mapping in A" time="0.000"/>
                                <testcase classname="Tests.Properties.Checked by SmallCheck" name="Testing filtering in B" time="0.003"/>
                                <testcase classname="Tests.Properties.Checked by SmallCheck" name="Testing mapping in B" time="0.000"/>
                                <testcase classname="Tests.Properties.Checked by SmallCheck" name="Testing filtering in C" time="0.001"/>
                                <testcase classname="Tests.Properties.Checked by SmallCheck" name="Testing mapping in C" time="0.000"/>
                            </testsuite>
                            <testsuite name="Checked by QuickCheck" tests="3">
                                <testcase classname="Tests.Properties.Checked by QuickCheck" name="Testing A against sample solution" time="0.001"/>
                                <testcase classname="Tests.Properties.Checked by QuickCheck" name="Testing B against sample solution" time="0.001"/>
                                <testcase classname="Tests.Properties.Checked by QuickCheck" name="Testing C against sample solution" time="0.001"/>
                            </testsuite>
                        </testsuite>
                        <testsuite name="Unit Tests" tests="3">
                            <testcase classname="Tests.Unit Tests" name="Testing selectAndReflectA (0,0) []" time="0.000"/>
                            <testcase classname="Tests.Unit Tests" name="Testing selectAndReflectB (0,1) [(0,0)]" time="0.000"/>
                            <testcase classname="Tests.Unit Tests" name="Testing selectAndReflectC (0,1) [(-1,-1)]" time="0.000"/>
                        </testsuite>
                    </testsuite>
                </testsuites>
                """;

        TestResultXmlParser.processTestResultFile(input, failedTests, successfulTests);

        // @formatter:off
        assertThat(successfulTests).extracting(LocalCITestJobDTO::name).containsExactlyInAnyOrder(
            "Properties.Checked by SmallCheck.Testing filtering in A",
                "Properties.Checked by SmallCheck.Testing mapping in A",
                "Properties.Checked by SmallCheck.Testing filtering in B",
                "Properties.Checked by SmallCheck.Testing mapping in B",
                "Properties.Checked by SmallCheck.Testing filtering in C",
                "Properties.Checked by SmallCheck.Testing mapping in C",
                "Properties.Checked by QuickCheck.Testing A against sample solution",
                "Properties.Checked by QuickCheck.Testing B against sample solution",
                "Properties.Checked by QuickCheck.Testing C against sample solution",
                "Unit Tests.Testing selectAndReflectA (0,0) []",
                "Unit Tests.Testing selectAndReflectB (0,1) [(0,0)]",
                "Unit Tests.Testing selectAndReflectC (0,1) [(-1,-1)]");
        // @formatter:on
        assertThat(failedTests).isEmpty();
    }

    @Test
    void testMultipleTestsuite() throws IOException {
        String input = """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuites>
                    <testsuite name="SuiteA" tests="3">
                        <testcase name="Test1"/>
                        <testcase name="Test2"/>
                        <testcase name="Test3"/>
                    </testsuite>
                    <testsuite name="SuiteB" tests="3">
                        <testcase name="Test1"/>
                        <testcase name="Test2"/>
                        <testcase name="Test3"/>
                    </testsuite>
                </testsuites>
                """;

        TestResultXmlParser.processTestResultFile(input, failedTests, successfulTests);

        // @formatter:off
        assertThat(successfulTests).extracting(LocalCITestJobDTO::name).containsExactlyInAnyOrder(
            "SuiteA.Test1",
                "SuiteA.Test2",
                "SuiteA.Test3",
                "SuiteB.Test1",
                "SuiteB.Test2",
                "SuiteB.Test3");
        // @formatter:on
        assertThat(failedTests).isEmpty();
    }

    @Test
    void testNestedTestsuiteMissingNames() throws IOException {
        String input = """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuites>
                    <testsuite>
                        <testsuite>
                            <testsuite>
                                <testcase name="Test1"/>
                                <testcase name="Test2"/>
                                <testcase name="Test3"/>
                            </testsuite>
                        </testsuite>
                    </testsuite>
                </testsuites>
                """;

        TestResultXmlParser.processTestResultFile(input, failedTests, successfulTests);

        assertThat(successfulTests).extracting(LocalCITestJobDTO::name).containsExactlyInAnyOrder("Test1", "Test2", "Test3");
        assertThat(failedTests).isEmpty();
    }

    @Test
    void testXmlProlog() throws IOException {
        String input = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!-- comment describing <testsuites> -->
                <!DOCTYPE testsuites>
                <testsuites>
                    <testsuite>
                        <testcase name="Test"/>
                    </testsuite>
                </testsuites>
                """;

        TestResultXmlParser.processTestResultFile(input, failedTests, successfulTests);

        assertThat(successfulTests).singleElement().extracting(LocalCITestJobDTO::name).isEqualTo("Test");
        assertThat(failedTests).isEmpty();
    }

    @Test
    void testRootTestsuiteNameIgnored() throws IOException {
        String input = """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="ignored">
                    <testsuite name="Suite">
                        <testcase name="Test"/>
                    </testsuite>
                </testsuite>
                """;

        TestResultXmlParser.processTestResultFile(input, failedTests, successfulTests);

        assertThat(successfulTests).singleElement().extracting(LocalCITestJobDTO::name).isEqualTo("Suite.Test");
        assertThat(failedTests).isEmpty();
    }

    @Test
    void testSingleTopLevelTestsuiteNameIgnored() throws IOException {
        String input = """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuites>
                    <testsuite name="ignored">
                        <testsuite name="Suite">
                            <testcase name="Test"/>
                        </testsuite>
                    </testsuite>
                </testsuites>
                """;

        TestResultXmlParser.processTestResultFile(input, failedTests, successfulTests);

        assertThat(successfulTests).singleElement().extracting(LocalCITestJobDTO::name).isEqualTo("Suite.Test");
        assertThat(failedTests).isEmpty();
    }

    @Test
    void testMixedNestedTestsuiteTestcase() throws IOException {
        String input = """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuites>
                    <testsuite>
                        <testcase name="Test"/>
                        <testsuite name="Suite">
                            <testcase name="Test"/>
                        </testsuite>
                    </testsuite>
                </testsuites>
                """;

        TestResultXmlParser.processTestResultFile(input, failedTests, successfulTests);

        assertThat(successfulTests).extracting(LocalCITestJobDTO::name).containsExactlyInAnyOrder("Test", "Suite.Test");
        assertThat(failedTests).isEmpty();
    }

    @Test
    void testClassnameExtraction() throws IOException {
        String input = """
                <testsuite>
                    <testcase name="testBubbleSort()" classname="de.tum.cit.SortingExampleBehaviorTest" time="0.029"/>
                    <testcase name="testMergeSort()" classname="de.tum.cit.SortingExampleBehaviorTest" time="0.026">
                        <failure message="Test failed"/>
                    </testcase>
                </testsuite>
                """;

        TestResultXmlParser.processTestResultFile(input, failedTests, successfulTests);

        assertThat(successfulTests).hasSize(1);
        assertThat(successfulTests.getFirst().name()).isEqualTo("testBubbleSort()");
        assertThat(successfulTests.getFirst().classname()).isEqualTo("de.tum.cit.SortingExampleBehaviorTest");

        assertThat(failedTests).hasSize(1);
        assertThat(failedTests.getFirst().name()).isEqualTo("testMergeSort()");
        assertThat(failedTests.getFirst().classname()).isEqualTo("de.tum.cit.SortingExampleBehaviorTest");
    }

    @Test
    void testInitializationErrorWithClassname() throws IOException {
        // This simulates what JUnit outputs when @BeforeAll fails
        String input = """
                <testsuite name="de.tum.cit.BehaviorTest" tests="1" errors="1">
                    <testcase name="initializationError" classname="de.tum.cit.BehaviorTest" time="0.001">
                        <error message="java.lang.RuntimeException: Setup failed">java.lang.RuntimeException: Setup failed</error>
                    </testcase>
                </testsuite>
                """;

        TestResultXmlParser.processTestResultFile(input, failedTests, successfulTests);

        assertThat(failedTests).hasSize(1);
        assertThat(failedTests.getFirst().name()).isEqualTo("initializationError");
        assertThat(failedTests.getFirst().classname()).isEqualTo("de.tum.cit.BehaviorTest");
        assertThat(failedTests.getFirst().testMessages()).contains("java.lang.RuntimeException: Setup failed");
    }

    @Test
    void testRealInitializationErrorFromArtemis() throws IOException {
        // This is the exact XML format produced by Artemis test execution when @BeforeAll fails
        String input = """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="de.tum.cit.aet.SortingExampleBehaviorTest" tests="1" skipped="0" failures="1" errors="0" timestamp="2026-01-18T18:52:53.257Z" hostname="Stephans-MacBook-Pro-2.local" time="0.0">
                  <properties/>
                  <testcase name="initializationError" classname="de.tum.cit.aet.SortingExampleBehaviorTest" time="0.0">
                    <failure message="java.lang.RuntimeException: error" type="java.lang.RuntimeException">java.lang.RuntimeException: error
                    at de.tum.cit.aet.SortingExampleBehaviorTest.init(SortingExampleBehaviorTest.java:32)
                    at java.base/java.lang.reflect.Method.invoke(Method.java:569)
                    </failure>
                  </testcase>
                  <system-out><![CDATA[]]></system-out>
                  <system-err><![CDATA[some log output]]></system-err>
                </testsuite>
                """;

        TestResultXmlParser.processTestResultFile(input, failedTests, successfulTests);

        assertThat(failedTests).hasSize(1);
        LocalCITestJobDTO testJob = failedTests.getFirst();
        assertThat(testJob.name()).isEqualTo("initializationError");
        assertThat(testJob.classname()).isEqualTo("de.tum.cit.aet.SortingExampleBehaviorTest");
        // The message should be extracted from the message attribute
        assertThat(testJob.testMessages().getFirst()).isEqualTo("java.lang.RuntimeException: error");

        // Verify that the TestCaseBase interface methods work correctly
        de.tum.cit.aet.artemis.programming.dto.TestCaseBase testCaseBase = testJob;
        assertThat(testCaseBase.name()).isEqualTo("initializationError");
        assertThat(testCaseBase.classname()).isEqualTo("de.tum.cit.aet.SortingExampleBehaviorTest");
    }
}
