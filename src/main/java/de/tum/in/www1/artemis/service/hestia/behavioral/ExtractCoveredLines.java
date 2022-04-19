package de.tum.in.www1.artemis.service.hestia.behavioral;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

class ExtractCoveredLines extends BehavioralKnowledgeSource {

    public ExtractCoveredLines(BehavioralBlackboard blackboard) {
        super(blackboard);
    }

    @Override
    public boolean executeCondition() {
        return blackboard.getGroupedFiles() != null && blackboard.getGroupedFiles().stream().anyMatch(groupedFile -> groupedFile.getCoveredLines() == null);
    }

    @Override
    public boolean executeAction() {
        for (GroupedFile groupedFile : blackboard.getGroupedFiles()) {
            groupedFile.setCoveredLines(groupedFile.getCoverageReportEntries().stream().flatMapToInt(
                    coverageReportEntry -> IntStream.rangeClosed(coverageReportEntry.getStartLine(), coverageReportEntry.getStartLine() + coverageReportEntry.getLineCount()))
                    .boxed().collect(Collectors.toSet()));
        }
        return !blackboard.getGroupedFiles().stream().allMatch(groupedFile -> groupedFile.getCoveredLines().isEmpty());
    }
}
