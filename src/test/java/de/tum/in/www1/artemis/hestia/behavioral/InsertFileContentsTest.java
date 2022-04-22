package de.tum.in.www1.artemis.hestia.behavioral;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.service.hestia.behavioral.BehavioralBlackboard;
import de.tum.in.www1.artemis.service.hestia.behavioral.BehavioralSolutionEntryGenerationException;
import de.tum.in.www1.artemis.service.hestia.behavioral.GroupedFile;
import de.tum.in.www1.artemis.service.hestia.behavioral.knowledgesource.InsertFileContents;

public class InsertFileContentsTest {

    private InsertFileContents insertFileContents;

    private GroupedFile groupedFile;

    private Map<String, String> solutionRepoFiles;

    @BeforeEach
    public void initBlackboard() {
        solutionRepoFiles = new HashMap<>();
        BehavioralBlackboard blackboard = new BehavioralBlackboard(null, null, solutionRepoFiles);
        var groupedFiles = new ArrayList<GroupedFile>();
        blackboard.setGroupedFiles(groupedFiles);
        groupedFile = new GroupedFile("test.java", null, null, null);
        groupedFiles.add(groupedFile);

        insertFileContents = new InsertFileContents(blackboard);
    }

    @Test
    public void testNoAction() {
        solutionRepoFiles.put("test.java", "Something else");
        groupedFile.setFileContent("Something");

        assertThat(insertFileContents.executeCondition()).isFalse();
    }

    @Test
    public void testAddContent() throws BehavioralSolutionEntryGenerationException {
        solutionRepoFiles.put("test.java", "Something");

        assertThat(insertFileContents.executeCondition()).isTrue();
        assertThat(insertFileContents.executeAction()).isTrue();
        assertThat(groupedFile.getFileContent()).isEqualTo("Something");
    }

    @Test
    public void testInvalidContent() {
        assertThat(insertFileContents.executeCondition()).isTrue();
        assertThatExceptionOfType(BehavioralSolutionEntryGenerationException.class).isThrownBy(() -> insertFileContents.executeAction());
    }
}
