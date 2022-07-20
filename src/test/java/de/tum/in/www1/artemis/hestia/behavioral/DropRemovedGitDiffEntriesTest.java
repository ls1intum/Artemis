package de.tum.in.www1.artemis.hestia.behavioral;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseGitDiffEntry;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseGitDiffReport;
import de.tum.in.www1.artemis.service.hestia.behavioral.BehavioralBlackboard;
import de.tum.in.www1.artemis.service.hestia.behavioral.BehavioralSolutionEntryGenerationException;
import de.tum.in.www1.artemis.service.hestia.behavioral.knowledgesource.DropRemovedGitDiffEntries;

public class DropRemovedGitDiffEntriesTest {

    private BehavioralBlackboard blackboard;

    private DropRemovedGitDiffEntries dropRemovedGitDiffEntries;

    @Test
    public void testNoAction() {
        var gitDiffReport = new ProgrammingExerciseGitDiffReport();
        gitDiffReport.setEntries(new HashSet<>());
        blackboard = new BehavioralBlackboard(gitDiffReport, null, null);
        dropRemovedGitDiffEntries = new DropRemovedGitDiffEntries(blackboard);
        assertThat(dropRemovedGitDiffEntries.executeCondition()).isFalse();
    }

    @Test
    public void testExecuteCondition() throws BehavioralSolutionEntryGenerationException {
        var gitDiffReport = new ProgrammingExerciseGitDiffReport();
        var removedEntry = new ProgrammingExerciseGitDiffEntry();
        var addedEntry = new ProgrammingExerciseGitDiffEntry();
        addedEntry.setStartLine(1);
        addedEntry.setLineCount(2);
        gitDiffReport.setEntries(Set.of(removedEntry, addedEntry));

        blackboard = new BehavioralBlackboard(gitDiffReport, null, null);
        dropRemovedGitDiffEntries = new DropRemovedGitDiffEntries(blackboard);

        assertThat(dropRemovedGitDiffEntries.executeCondition()).isTrue();
        assertThat(dropRemovedGitDiffEntries.executeAction()).isTrue();
        var remainingEntries = blackboard.getGitDiffReport().getEntries();
        assertThat(dropRemovedGitDiffEntries.executeCondition()).isFalse();
        assertThat(remainingEntries).containsExactly(addedEntry);
    }
}
