package de.tum.in.www1.artemis.hestia.behavioral;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.service.hestia.behavioral.BehavioralBlackboard;
import de.tum.in.www1.artemis.service.hestia.behavioral.GroupedFile;
import de.tum.in.www1.artemis.service.hestia.behavioral.knowledgesource.AddUncoveredLinesAsPotentialCodeBlocks;

class AddUncoveredLinesAsPotentialCodeBlocksTest {

    private AddUncoveredLinesAsPotentialCodeBlocks addPotentialCodeBlocks;

    private GroupedFile groupedFile;

    @BeforeEach
    void initBlackboard() {
        BehavioralBlackboard blackboard = new BehavioralBlackboard(null, null, null);
        var groupedFiles = new ArrayList<GroupedFile>();
        blackboard.setGroupedFiles(groupedFiles);
        groupedFile = new GroupedFile("test.java", null, null, null);
        groupedFile.setCommonChanges(List.of(new GroupedFile.ChangeBlock(List.of(3, 4))));
        groupedFiles.add(groupedFile);

        addPotentialCodeBlocks = new AddUncoveredLinesAsPotentialCodeBlocks(blackboard);
    }

    @Test
    void testNoAction() {
        assertThat(addPotentialCodeBlocks.executeCondition()).isFalse();
    }

    @Test
    void testAddNoBlocks() {
        groupedFile.setFileContent("""
                A
                B
                C
                D
                E
                F
                """);

        assertThat(addPotentialCodeBlocks.executeCondition()).isTrue();
        assertThat(addPotentialCodeBlocks.executeAction()).isFalse();
        assertThat(groupedFile.getCommonChanges()).isNotNull().containsExactly(new GroupedFile.ChangeBlock(List.of(3, 4)));
    }

    @Test
    void testAddPrefix() {
        groupedFile.setFileContent("""


                C
                D
                E
                F
                """);

        assertThat(addPotentialCodeBlocks.executeCondition()).isTrue();
        assertThat(addPotentialCodeBlocks.executeAction()).isTrue();
        assertThat(groupedFile.getCommonChanges()).isNotNull().containsExactlyInAnyOrder(new GroupedFile.ChangeBlock(List.of(3, 4)),
                new GroupedFile.ChangeBlock(List.of(1, 2), true));
    }

    @Test
    void testAddPostfix() {
        groupedFile.setFileContent("""
                A
                B
                C
                D


                X
                """);

        assertThat(addPotentialCodeBlocks.executeCondition()).isTrue();
        assertThat(addPotentialCodeBlocks.executeAction()).isTrue();
        assertThat(groupedFile.getCommonChanges()).isNotNull().containsExactlyInAnyOrder(new GroupedFile.ChangeBlock(List.of(3, 4)),
                new GroupedFile.ChangeBlock(List.of(5, 6), true));
    }

    @Test
    void testIncludesCurlyBraces() {
        groupedFile.setFileContent("""
                A
                }
                C
                D
                {
                F
                """);

        assertThat(addPotentialCodeBlocks.executeCondition()).isTrue();
        assertThat(addPotentialCodeBlocks.executeAction()).isTrue();
        assertThat(groupedFile.getCommonChanges()).isNotNull().containsExactlyInAnyOrder(new GroupedFile.ChangeBlock(List.of(3, 4)), new GroupedFile.ChangeBlock(List.of(2), true),
                new GroupedFile.ChangeBlock(List.of(5), true));
    }

    @Test
    void testIncludesElseStatements() {
        groupedFile.setFileContent("""
                else
                } else {
                C
                D
                } else
                else {
                """);

        assertThat(addPotentialCodeBlocks.executeCondition()).isTrue();
        assertThat(addPotentialCodeBlocks.executeAction()).isTrue();
        assertThat(groupedFile.getCommonChanges()).isNotNull().containsExactlyInAnyOrder(new GroupedFile.ChangeBlock(List.of(3, 4)),
                new GroupedFile.ChangeBlock(List.of(1, 2), true), new GroupedFile.ChangeBlock(List.of(5, 6), true));
    }
}
