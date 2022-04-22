package de.tum.in.www1.artemis.hestia.behavioral;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.service.hestia.behavioral.BehavioralBlackboard;
import de.tum.in.www1.artemis.service.hestia.behavioral.GroupedFile;
import de.tum.in.www1.artemis.service.hestia.behavioral.knowledgesource.FindCommonLines;

public class FindCommonLinesTest {

    private FindCommonLines findCommonLines;

    private GroupedFile groupedFile;

    @BeforeEach
    public void initBlackboard() {
        BehavioralBlackboard blackboard = new BehavioralBlackboard(null, null, null);
        var groupedFiles = new ArrayList<GroupedFile>();
        blackboard.setGroupedFiles(groupedFiles);
        groupedFile = new GroupedFile("test.java", null, null, null);
        groupedFiles.add(groupedFile);

        findCommonLines = new FindCommonLines(blackboard);
    }

    @Test
    public void testNoAction() {
        assertThat(findCommonLines.executeCondition()).isFalse();
    }

    @Test
    public void testFindCommonLines() {
        groupedFile.setCoveredLines(Set.of(1, 2, 3));
        groupedFile.setChangedLines(Set.of(2, 3, 4));

        assertThat(findCommonLines.executeCondition()).isTrue();
        assertThat(findCommonLines.executeAction()).isTrue();
        assertThat(groupedFile.getCommonLines()).containsExactly(2, 3);
    }

    @Test
    public void testFindNoCommonLines() {
        groupedFile.setCoveredLines(Set.of(1, 2, 3));
        groupedFile.setChangedLines(Set.of(4, 5, 6));

        assertThat(findCommonLines.executeCondition()).isTrue();
        assertThat(findCommonLines.executeAction()).isFalse();
        assertThat(groupedFile.getCommonLines()).isEmpty();
    }
}
