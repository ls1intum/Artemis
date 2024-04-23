package de.tum.in.www1.artemis.localvcci;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.service.connectors.localci.buildagent.TestResultXmlParser;
import de.tum.in.www1.artemis.service.connectors.localci.dto.LocalCIBuildResult;

class TestResultXmlParserTest {

    private final List<LocalCIBuildResult.LocalCITestJobDTO> failedTests = new ArrayList<>();

    private final List<LocalCIBuildResult.LocalCITestJobDTO> successfulTests = new ArrayList<>();

    @Test
    void testParseResultXmlInnerText() throws IOException {
        String exampleXml = """
                 <testsuite>
                     <testcase name='testBubbleSort()' classname="testpackage.SortingExampleBehaviorTest" time='0.000306'>
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
                     <testcase name='testBubbleSort()' classname="testpackage.SortingExampleBehaviorTest" time='0.000306'>
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
}
