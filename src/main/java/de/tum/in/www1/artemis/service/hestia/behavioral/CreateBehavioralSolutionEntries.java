package de.tum.in.www1.artemis.service.hestia.behavioral;

import java.util.ArrayList;

class CreateBehavioralSolutionEntries extends BehavioralKnowledgeSource {

    public CreateBehavioralSolutionEntries(BehavioralBlackboard blackboard) {
        super(blackboard);
    }

    @Override
    public boolean executeCondition() {
        return blackboard.getGroupedFiles() != null && blackboard.getGroupedFiles().stream().noneMatch(groupedFile -> groupedFile.getCommonChanges() == null);
    }

    @Override
    public boolean executeAction() {
        var solutionEntries = new ArrayList<BehavioralSolutionEntry>();
        for (GroupedFile groupedFile : blackboard.getGroupedFiles()) {
            for (GroupedFile.ChangeBlock commonChange : groupedFile.getCommonChanges()) {
                solutionEntries
                        .add(new BehavioralSolutionEntry(groupedFile.getFilePath(), groupedFile.getTestCase(), commonChange.getLines().first(), commonChange.getLines().size()));
            }
        }
        if (solutionEntries.equals(blackboard.getSolutionEntries())) {
            return false;
        }
        blackboard.setSolutionEntries(solutionEntries);
        return !blackboard.getSolutionEntries().isEmpty();
    }
}
