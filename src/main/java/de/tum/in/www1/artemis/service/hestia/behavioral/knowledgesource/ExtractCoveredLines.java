package de.tum.in.www1.artemis.service.hestia.behavioral.knowledgesource;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import de.tum.in.www1.artemis.service.hestia.behavioral.BehavioralBlackboard;
import de.tum.in.www1.artemis.service.hestia.behavioral.GroupedFile;

/**
 * For each {@link GroupedFile}:
 * Extracts the lines that were covered by the test case in the file (both of the GroupedFile) from the
 * {@link de.tum.in.www1.artemis.domain.hestia.TestwiseCoverageReportEntry}s
 */
public class ExtractCoveredLines extends BehavioralKnowledgeSource {

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
            groupedFile.setCoveredLines(groupedFile.getCoverageReportEntries().stream()
                    .flatMapToInt(
                            coverageReportEntry -> IntStream.range(coverageReportEntry.getStartLine(), coverageReportEntry.getStartLine() + coverageReportEntry.getLineCount()))
                    .boxed().collect(Collectors.toSet()));
        }
        return !blackboard.getGroupedFiles().stream().allMatch(groupedFile -> groupedFile.getCoveredLines().isEmpty());
    }
}
