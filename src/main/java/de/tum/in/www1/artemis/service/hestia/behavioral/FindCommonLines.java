package de.tum.in.www1.artemis.service.hestia.behavioral;

import java.util.ArrayList;
import java.util.List;

class FindCommonLines extends BehavioralKnowledgeSource {

    public FindCommonLines(BehavioralBlackboard blackboard) {
        super(blackboard);
    }

    @Override
    public boolean executeCondition() {
        return blackboard.getGroupedFiles() != null && blackboard.getGroupedFiles().stream().noneMatch(groupedFile -> groupedFile.getChangedLines() == null)
                && blackboard.getGroupedFiles().stream().noneMatch(groupedFile -> groupedFile.getCoveredLines() == null)
                && blackboard.getGroupedFiles().stream().anyMatch(groupedFile -> groupedFile.getCommonLines() == null);
    }

    @Override
    public boolean executeAction() {
        for (GroupedFile groupedFile : blackboard.getGroupedFiles()) {
            List<Integer> commonLines = new ArrayList<>(groupedFile.getCoveredLines());
            commonLines.retainAll(groupedFile.getChangedLines());
            groupedFile.setCommonLines(commonLines);
        }
        return !blackboard.getGroupedFiles().stream().allMatch(groupedFile -> groupedFile.getCommonLines().isEmpty());
    }
}
