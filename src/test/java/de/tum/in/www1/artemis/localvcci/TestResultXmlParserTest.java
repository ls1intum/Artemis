package de.tum.in.www1.artemis.localvcci;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.service.connectors.localci.buildagent.TestResultXmlParser;
import de.tum.in.www1.artemis.service.connectors.localci.dto.LocalCIBuildResult;

public class TestResultXmlParserTest {

    @Test
    void testParseResultXml1() throws IOException {

        List<LocalCIBuildResult.LocalCITestJobDTO> failedTests = new ArrayList<>();
        List<LocalCIBuildResult.LocalCITestJobDTO> successfulTests = new ArrayList<>();

        String exampleXml = """
                 <testsuite>
                     <testcase name='testBubbleSort()' classname="testpackage.SortingExampleBehaviorTest" time='0.000306'>
                         <failure>test `add` failed on ≥ 1 cases:
                             (0, 0)
                             Your submission raised an error Failure(&quot;TODO add&quot;)
                         </failure>
                     </testcase>
                 </testsuite>
                """;

        TestResultXmlParser.processTestResultFile(exampleXml, failedTests, successfulTests);
        assertThat(failedTests).hasSize(1);
        // TODO: add more assertions
    }

    @Test
    void testParseResultXml2() throws IOException {
        List<LocalCIBuildResult.LocalCITestJobDTO> failedTests = new ArrayList<>();
        List<LocalCIBuildResult.LocalCITestJobDTO> successfulTests = new ArrayList<>();

        String exampleXml = """
                 <testsuite>
                     <testcase name='testBubbleSort()' classname="testpackage.SortingExampleBehaviorTest" time='0.000306'>
                         <failure message = "test `add` failed"/>
                     </testcase>
                 </testsuite>
                """;

        TestResultXmlParser.processTestResultFile(exampleXml, failedTests, successfulTests);
        assertThat(failedTests).hasSize(1);
        // TODO: add more assertions
    }
}
