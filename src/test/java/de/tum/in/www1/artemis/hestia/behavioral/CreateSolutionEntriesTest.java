package de.tum.in.www1.artemis.hestia.behavioral;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseSolutionEntry;
import de.tum.in.www1.artemis.service.hestia.behavioral.BehavioralBlackboard;
import de.tum.in.www1.artemis.service.hestia.behavioral.GroupedFile;
import de.tum.in.www1.artemis.service.hestia.behavioral.knowledgesource.CreateSolutionEntries;

public class CreateSolutionEntriesTest {

    private BehavioralBlackboard blackboard;

    private CreateSolutionEntries createSolutionEntries;

    private GroupedFile groupedFile;

    @BeforeEach
    public void initBlackboard() {
        blackboard = new BehavioralBlackboard(null, null, null);
        var groupedFiles = new ArrayList<GroupedFile>();
        blackboard.setGroupedFiles(groupedFiles);
        groupedFile = new GroupedFile("test.java", new ProgrammingExerciseTestCase(), null, null);
        groupedFiles.add(groupedFile);

        createSolutionEntries = new CreateSolutionEntries(blackboard);
    }

    @Test
    public void testNoAction() {
        assertThat(createSolutionEntries.executeCondition()).isFalse();
    }

    @Test
    public void testNoChangesOnSecondCall() {
        groupedFile.setFileContent("A\nB\nC\nD");
        groupedFile.setCommonChanges(List.of(new GroupedFile.ChangeBlock(List.of(2, 3), false)));

        assertThat(createSolutionEntries.executeCondition()).isTrue();
        assertThat(createSolutionEntries.executeAction()).isTrue();
        var solutionEntries = blackboard.getSolutionEntries();
        assertThat(createSolutionEntries.executeAction()).isFalse();
        assertThat(blackboard.getSolutionEntries()).isSameAs(solutionEntries);
    }

    @Test
    public void testCreateOneSolutionEntry() {
        groupedFile.setFileContent("A\nB\nC\nD");
        groupedFile.setCommonChanges(List.of(new GroupedFile.ChangeBlock(List.of(2, 3), false)));

        assertThat(createSolutionEntries.executeCondition()).isTrue();
        assertThat(createSolutionEntries.executeAction()).isTrue();
        var expected = new ProgrammingExerciseSolutionEntry();
        expected.setId(0L);
        expected.setFilePath("test.java");
        expected.setTestCase(groupedFile.getTestCase());
        expected.setLine(2);
        expected.setCode("B\nC");
        assertThat(blackboard.getSolutionEntries()).isNotNull().containsExactly(expected);
    }

    @Test
    public void testCreateOneSolutionEntryIgnoringPotential() {
        groupedFile.setFileContent("A\nB\nC\nD\nE");
        groupedFile.setCommonChanges(List.of(new GroupedFile.ChangeBlock(List.of(2, 3), false), new GroupedFile.ChangeBlock(List.of(4, 5), true)));

        assertThat(createSolutionEntries.executeCondition()).isTrue();
        assertThat(createSolutionEntries.executeAction()).isTrue();
        var expected = new ProgrammingExerciseSolutionEntry();
        expected.setId(0L);
        expected.setFilePath("test.java");
        expected.setTestCase(groupedFile.getTestCase());
        expected.setLine(2);
        expected.setCode("B\nC");
        assertThat(blackboard.getSolutionEntries()).isNotNull().containsExactly(expected);
    }

    @Test
    public void testCreateTwoSolutionEntries() {
        groupedFile.setFileContent("A\nB\nC\nD\nE");
        groupedFile.setCommonChanges(List.of(new GroupedFile.ChangeBlock(List.of(2, 3), false), new GroupedFile.ChangeBlock(List.of(4, 5), false)));

        assertThat(createSolutionEntries.executeCondition()).isTrue();
        assertThat(createSolutionEntries.executeAction()).isTrue();
        var expected1 = new ProgrammingExerciseSolutionEntry();
        expected1.setId(0L);
        expected1.setFilePath("test.java");
        expected1.setTestCase(groupedFile.getTestCase());
        expected1.setLine(2);
        expected1.setCode("B\nC");
        var expected2 = new ProgrammingExerciseSolutionEntry();
        expected2.setId(0L);
        expected2.setFilePath("test.java");
        expected2.setTestCase(groupedFile.getTestCase());
        expected2.setLine(4);
        expected2.setCode("D\nE");
        assertThat(blackboard.getSolutionEntries()).isNotNull().containsExactlyInAnyOrder(expected1, expected2);
    }
}
