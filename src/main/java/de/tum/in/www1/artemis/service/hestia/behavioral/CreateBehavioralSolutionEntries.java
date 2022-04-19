package de.tum.in.www1.artemis.service.hestia.behavioral;

import java.util.ArrayList;

class CreateBehavioralSolutionEntries extends BehavioralKnowledgeSource {

    public CreateBehavioralSolutionEntries(BehavioralBlackboard blackboard) {
        super(blackboard);
    }

    @Override
    public boolean executeCondition() {
        return blackboard.getGroupedFiles() != null && blackboard.getGroupedFiles().stream().noneMatch(groupedFile -> groupedFile.getCommonLines() == null);
    }

    @Override
    public boolean executeAction() {
        var solutionEntries = new ArrayList<BehavioralSolutionEntry>();
        for (GroupedFile groupedFile : blackboard.getGroupedFiles()) {
            var commonLines = groupedFile.getCommonLines();
            commonLines.sort(Integer::compareTo);

            Integer previousLine = null;
            Integer startLine = null;
            int lineCount = 0;

            for (Integer currentLine : commonLines) {
                if (startLine == null) {
                    startLine = currentLine;
                }
                if (previousLine != null && currentLine - 1 > previousLine) {
                    solutionEntries.add(new BehavioralSolutionEntry(groupedFile.getFilePath(), groupedFile.getTestCase(), startLine, lineCount));
                    lineCount = 0;
                    startLine = currentLine;
                }
                lineCount++;
                previousLine = currentLine;
            }
        }
        blackboard.setSolutionEntries(solutionEntries);
        return !blackboard.getSolutionEntries().isEmpty();
    }
}
