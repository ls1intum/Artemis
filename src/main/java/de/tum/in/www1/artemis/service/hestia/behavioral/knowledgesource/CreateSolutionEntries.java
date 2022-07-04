package de.tum.in.www1.artemis.service.hestia.behavioral.knowledgesource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseSolutionEntry;
import de.tum.in.www1.artemis.service.hestia.behavioral.BehavioralBlackboard;
import de.tum.in.www1.artemis.service.hestia.behavioral.GroupedFile;

/**
 * For each {@link GroupedFile}:
 * Creates one {@link ProgrammingExerciseSolutionEntry} for each non-potential {@link GroupedFile.ChangeBlock}
 */
public class CreateSolutionEntries extends BehavioralKnowledgeSource {

    public CreateSolutionEntries(BehavioralBlackboard blackboard) {
        super(blackboard);
    }

    @Override
    public boolean executeCondition() {
        return blackboard.getGroupedFiles() != null && blackboard.getGroupedFiles().stream().noneMatch(groupedFile -> groupedFile.getCommonChanges() == null);
    }

    @Override
    public boolean executeAction() {
        var solutionEntries = new ArrayList<ProgrammingExerciseSolutionEntry>();
        for (GroupedFile groupedFile : blackboard.getGroupedFiles()) {
            for (GroupedFile.ChangeBlock changeBlock : groupedFile.getCommonChanges()) {
                if (!changeBlock.isPotential()) {
                    solutionEntries.add(createSolutionEntry(groupedFile, changeBlock));
                }
            }
        }
        if (solutionEntries.equals(blackboard.getSolutionEntries())) {
            return false;
        }
        blackboard.setSolutionEntries(solutionEntries);
        return !blackboard.getSolutionEntries().isEmpty();
    }

    private ProgrammingExerciseSolutionEntry createSolutionEntry(GroupedFile groupedFile, GroupedFile.ChangeBlock changeBlock) {
        var solutionEntry = new ProgrammingExerciseSolutionEntry();
        // Set temporary id, as equals checks won't work otherwise
        solutionEntry.setId(0L);
        solutionEntry.setLine(changeBlock.getLines().first());
        solutionEntry.setFilePath(groupedFile.getFilePath());
        solutionEntry.setTestCase(groupedFile.getTestCase());
        var fileContent = groupedFile.getFileContent();
        if (fileContent != null) {
            var code = Arrays.stream(fileContent.split("\n")).skip(changeBlock.getLines().first() - 1).limit(changeBlock.getLines().size()).collect(Collectors.joining("\n"));
            solutionEntry.setCode(code);
        }
        return solutionEntry;
    }
}
