package de.tum.cit.aet.artemis.service.connectors.localci.buildagent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.buildagent.dto.BuildResult;
import de.tum.cit.aet.artemis.buildagent.service.TestResultXmlParser;

class TestResultXmlParserTest {

    private final List<BuildResult.LocalCITestJobDTO> failedTests = new ArrayList<>();

    private final List<BuildResult.LocalCITestJobDTO> successfulTests = new ArrayList<>();

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
        assertThat(test.getName()).isEqualTo("testBubbleSort()");
        assertThat(test.getTestMessages()).containsExactly("""
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
        assertThat(test.getName()).isEqualTo("testBubbleSort()");
        assertThat(test.getTestMessages()).containsExactly("test `add` failed");
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
        assertThat(test.getName()).isEqualTo("testMergeSort()");
        assertThat(failedTests.getFirst().getTestMessages()).containsExactly("org.opentest4j.AssertionFailedError: Deine Einreichung enthält keine Ausgabe. (67cac2)");
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
        assertThat(successfulTests).map(test -> test.getName()).containsExactlyInAnyOrder("testMergeSort()", "testUseBubbleSortForSmallList()", "testBubbleSort()",
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
        assertThat(test.getName()).isEqualTo("CompileLinkedList");
        assertThat(test.getTestMessages().getFirst()).isEqualTo("Build for directory ../assignment/build failed. Returncode is 2.");
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
        assertThat(test.getName()).isEqualTo("mwe-name");
        assertThat(test.getTestMessages()).hasSize(1).contains("");
    }
}
