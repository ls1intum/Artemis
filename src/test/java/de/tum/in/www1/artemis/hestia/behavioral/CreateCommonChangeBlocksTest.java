package de.tum.in.www1.artemis.hestia.behavioral;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.service.hestia.behavioral.BehavioralBlackboard;
import de.tum.in.www1.artemis.service.hestia.behavioral.GroupedFile;
import de.tum.in.www1.artemis.service.hestia.behavioral.knowledgesource.CreateCommonChangeBlocks;

public class CreateCommonChangeBlocksTest {

    private CreateCommonChangeBlocks createCommonChangeBlocks;

    private GroupedFile groupedFile;

    @BeforeEach
    public void initBlackboard() {
        BehavioralBlackboard blackboard = new BehavioralBlackboard(null, null, null);
        var groupedFiles = new ArrayList<GroupedFile>();
        blackboard.setGroupedFiles(groupedFiles);
        groupedFile = new GroupedFile("test.java", null, null, null);
        groupedFiles.add(groupedFile);

        createCommonChangeBlocks = new CreateCommonChangeBlocks(blackboard);
    }

    @Test
    public void testNoAction() {
        groupedFile.setCommonLines(Collections.emptyList());
        groupedFile.setCommonChanges(Collections.emptyList());
        assertThat(createCommonChangeBlocks.executeCondition()).isFalse();
    }

    @Test
    public void testCreateOneChangeBlock() {
        groupedFile.setCommonLines(Set.of(1, 2, 3));

        assertThat(createCommonChangeBlocks.executeCondition()).isTrue();
        assertThat(createCommonChangeBlocks.executeAction()).isTrue();
        assertThat(groupedFile.getCommonChanges()).containsExactly(new GroupedFile.ChangeBlock(List.of(1, 2, 3)));
    }

    @Test
    public void testCreateTwoChangeBlocks() {
        groupedFile.setCommonLines(Set.of(1, 2, 4, 5));

        assertThat(createCommonChangeBlocks.executeCondition()).isTrue();
        assertThat(createCommonChangeBlocks.executeAction()).isTrue();
        assertThat(groupedFile.getCommonChanges()).containsExactly(new GroupedFile.ChangeBlock(List.of(1, 2)), new GroupedFile.ChangeBlock(List.of(4, 5)));
    }
}
