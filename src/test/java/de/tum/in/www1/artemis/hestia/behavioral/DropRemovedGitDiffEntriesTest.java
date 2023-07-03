package de.tum.in.www1.artemis.hestia.behavioral;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseGitDiffEntry;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseGitDiffReport;
import de.tum.in.www1.artemis.service.hestia.behavioral.BehavioralBlackboard;
import de.tum.in.www1.artemis.service.hestia.behavioral.BehavioralSolutionEntryGenerationException;
import de.tum.in.www1.artemis.service.hestia.behavioral.knowledgesource.DropRemovedGitDiffEntries;

class DropRemovedGitDiffEntriesTest {

    private BehavioralBlackboard blackboard;

    private ProgrammingExerciseGitDiffReport gitDiffReport;

    private DropRemovedGitDiffEntries dropRemovedGitDiffEntries;

    @BeforeEach
    void setup() {
        gitDiffReport = new ProgrammingExerciseGitDiffReport();
        gitDiffReport.setEntries(new HashSet<>());

        blackboard = new BehavioralBlackboard(gitDiffReport, null, null);
        dropRemovedGitDiffEntries = new DropRemovedGitDiffEntries(blackboard);

    }

    @Test
    void testNoAction() {
        assertThat(dropRemovedGitDiffEntries.executeCondition()).isFalse();
    }

    @Test
    void testExecuteCondition() throws BehavioralSolutionEntryGenerationException {
        var removedEntry = new ProgrammingExerciseGitDiffEntry();
        var addedEntry = new ProgrammingExerciseGitDiffEntry();
        addedEntry.setStartLine(1);
        addedEntry.setLineCount(2);
        gitDiffReport.setEntries(Set.of(removedEntry, addedEntry));

        assertThat(dropRemovedGitDiffEntries.executeCondition()).isTrue();
        assertThat(dropRemovedGitDiffEntries.executeAction()).isTrue();
        var remainingEntries = blackboard.getGitDiffReport().getEntries();
        assertThat(dropRemovedGitDiffEntries.executeCondition()).isFalse();
        assertThat(remainingEntries).containsExactly(addedEntry);
    }
}
