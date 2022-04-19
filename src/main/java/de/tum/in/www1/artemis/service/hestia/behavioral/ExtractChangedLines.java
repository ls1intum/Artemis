package de.tum.in.www1.artemis.service.hestia.behavioral;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

class ExtractChangedLines extends BehavioralKnowledgeSource {

    public ExtractChangedLines(BehavioralBlackboard blackboard) {
        super(blackboard);
    }

    @Override
    public boolean executeCondition() {
        return blackboard.getGroupedFiles() != null && blackboard.getGroupedFiles().stream().anyMatch(groupedFile -> groupedFile.getChangedLines() == null);
    }

    @Override
    public boolean executeAction() {
        for (GroupedFile groupedFile : blackboard.getGroupedFiles()) {
            groupedFile.setChangedLines(groupedFile.getGitDiffEntries().stream()
                    .flatMapToInt(gitDiffEntry -> IntStream.rangeClosed(gitDiffEntry.getStartLine(), gitDiffEntry.getStartLine() + gitDiffEntry.getLineCount())).boxed()
                    .collect(Collectors.toSet()));
        }
        return !blackboard.getGroupedFiles().stream().allMatch(groupedFile -> groupedFile.getChangedLines().isEmpty());
    }
}
