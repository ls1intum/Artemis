package de.tum.cit.aet.artemis.service.hestia.behavioral.knowledgesource;

import java.util.HashSet;
import java.util.stream.Collectors;

import de.tum.cit.aet.artemis.programming.domain.hestia.ProgrammingExerciseGitDiffEntry;
import de.tum.cit.aet.artemis.programming.domain.hestia.ProgrammingExerciseGitDiffReport;
import de.tum.cit.aet.artemis.service.hestia.behavioral.BehavioralBlackboard;

/**
 * Remove all {@link ProgrammingExerciseGitDiffEntry} from the
 * {@link ProgrammingExerciseGitDiffReport} of the {@link BehavioralBlackboard}
 * that represents consecutive blocks of removed code.
 * Entries cannot be generated for removed code, therefore we have to drop them from the git diff report of the blackboard.
 */
public class DropRemovedGitDiffEntries extends BehavioralKnowledgeSource {

    public DropRemovedGitDiffEntries(BehavioralBlackboard blackboard) {
        super(blackboard);
    }

    @Override
    public boolean executeCondition() {
        return blackboard.getGitDiffReport() != null && blackboard.getGitDiffReport().getEntries() != null
                && blackboard.getGitDiffReport().getEntries().stream().anyMatch(entry -> entry.getStartLine() == null || entry.getLineCount() == null);
    }

    @Override
    public boolean executeAction() {
        var nonRemovedEntries = blackboard.getGitDiffReport().getEntries().stream().filter(entry -> entry.getStartLine() != null && entry.getLineCount() != null)
                .collect(Collectors.toCollection(HashSet::new));
        blackboard.getGitDiffReport().setEntries(nonRemovedEntries);
        return !nonRemovedEntries.isEmpty();
    }
}
