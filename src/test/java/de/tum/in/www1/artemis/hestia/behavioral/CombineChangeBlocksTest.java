package de.tum.in.www1.artemis.hestia.behavioral;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.service.hestia.behavioral.BehavioralBlackboard;
import de.tum.in.www1.artemis.service.hestia.behavioral.BehavioralSolutionEntryGenerationException;
import de.tum.in.www1.artemis.service.hestia.behavioral.GroupedFile;
import de.tum.in.www1.artemis.service.hestia.behavioral.GroupedFile.ChangeBlock;
import de.tum.in.www1.artemis.service.hestia.behavioral.knowledgesource.CombineChangeBlocks;

class CombineChangeBlocksTest {

    private CombineChangeBlocks combineChangeBlocks;

    private GroupedFile groupedFile;

    @BeforeEach
    void initBlackboard() {
        BehavioralBlackboard blackboard = new BehavioralBlackboard(null, null, null);
        var groupedFiles = new ArrayList<GroupedFile>();
        blackboard.setGroupedFiles(groupedFiles);
        groupedFile = new GroupedFile("test.java", null, null, null);
        groupedFiles.add(groupedFile);

        combineChangeBlocks = new CombineChangeBlocks(blackboard);
    }

    @Test
    void testNoCombining() throws BehavioralSolutionEntryGenerationException {
        var changeBlocks = new ChangeBlock[] { new ChangeBlock(List.of(1)), new ChangeBlock(List.of(3)), new ChangeBlock(List.of(5)) };
        groupedFile.setCommonChanges(List.of(changeBlocks));

        assertThat(combineChangeBlocks.executeCondition()).isTrue();
        assertThat(combineChangeBlocks.executeAction()).isFalse();
        assertThat(groupedFile.getCommonChanges()).containsExactly(changeBlocks);
    }

    @Test
    void testOneCombination1() throws BehavioralSolutionEntryGenerationException {
        var changeBlocks = new ChangeBlock[] { new ChangeBlock(List.of(2)), new ChangeBlock(List.of(3)) };
        groupedFile.setCommonChanges(List.of(changeBlocks));

        assertThat(combineChangeBlocks.executeCondition()).isTrue();
        assertThat(combineChangeBlocks.executeAction()).isTrue();
        assertThat(groupedFile.getCommonChanges()).containsExactly(new ChangeBlock(List.of(2, 3)));
    }

    @Test
    void testOneCombination2() throws BehavioralSolutionEntryGenerationException {
        var changeBlocks = new ChangeBlock[] { new ChangeBlock(List.of(1, 2, 3, 4)), new ChangeBlock(List.of(3, 4, 5, 6, 7, 8)) };
        groupedFile.setCommonChanges(List.of(changeBlocks));

        assertThat(combineChangeBlocks.executeCondition()).isTrue();
        assertThat(combineChangeBlocks.executeAction()).isTrue();
        assertThat(groupedFile.getCommonChanges()).containsExactly(new ChangeBlock(List.of(1, 2, 3, 4, 5, 6, 7, 8)));
    }

    @Test
    void testCombineManyIntoOne1() throws BehavioralSolutionEntryGenerationException {
        var changeBlocks = new ChangeBlock[] { new ChangeBlock(List.of(2)), new ChangeBlock(List.of(3)), new ChangeBlock(List.of(4)), new ChangeBlock(List.of(5)) };
        groupedFile.setCommonChanges(List.of(changeBlocks));

        assertThat(combineChangeBlocks.executeCondition()).isTrue();
        assertThat(combineChangeBlocks.executeAction()).isTrue();
        assertThat(groupedFile.getCommonChanges()).containsExactly(new ChangeBlock(List.of(2, 3, 4, 5)));
    }

    @Test
    void testCombineManyIntoOne2() throws BehavioralSolutionEntryGenerationException {
        var changeBlocks = new ChangeBlock[] { new ChangeBlock(List.of(1, 2, 3)), new ChangeBlock(List.of(3, 4, 5)), new ChangeBlock(List.of(4, 5, 6)),
                new ChangeBlock(List.of(7, 8, 9)) };
        groupedFile.setCommonChanges(List.of(changeBlocks));

        assertThat(combineChangeBlocks.executeCondition()).isTrue();
        assertThat(combineChangeBlocks.executeAction()).isTrue();
        assertThat(groupedFile.getCommonChanges()).containsExactly(new ChangeBlock(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9)));
    }
}
