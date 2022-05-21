package de.tum.in.www1.artemis.service.hestia.behavioral.knowledgesource;

import java.util.TreeSet;
import java.util.stream.IntStream;

import de.tum.in.www1.artemis.service.hestia.behavioral.BehavioralBlackboard;
import de.tum.in.www1.artemis.service.hestia.behavioral.GroupedFile;

/**
 * For each {@link GroupedFile}:
 * Takes the common lines (intersection of covered and changed lines) and creates {@link de.tum.in.www1.artemis.service.hestia.behavioral.GroupedFile.ChangeBlock}s
 * from them. Each ChangeBlock represents one continuous block of common lines.
 */
public class CreateCommonChangeBlocks extends BehavioralKnowledgeSource {

    public CreateCommonChangeBlocks(BehavioralBlackboard blackboard) {
        super(blackboard);
    }

    @Override
    public boolean executeCondition() {
        return blackboard.getGroupedFiles() != null && blackboard.getGroupedFiles().stream().noneMatch(groupedFile -> groupedFile.getCommonLines() == null)
                && blackboard.getGroupedFiles().stream().anyMatch(groupedFile -> groupedFile.getCommonChanges() == null);
    }

    @Override
    public boolean executeAction() {
        boolean didChanges = false;

        for (GroupedFile groupedFile : blackboard.getGroupedFiles()) {
            var changeBlocks = new TreeSet<GroupedFile.ChangeBlock>();

            Integer previousLine = null;
            Integer startLine = null;
            int lineCount = 0;

            for (Integer currentLine : groupedFile.getCommonLines()) {
                if (startLine == null) {
                    startLine = currentLine;
                }
                // Check if this is a new change block
                if (previousLine != null && currentLine - 1 > previousLine) {
                    changeBlocks.add(new GroupedFile.ChangeBlock(IntStream.range(startLine, startLine + lineCount).boxed().toList()));
                    lineCount = 0;
                    startLine = currentLine;
                }
                lineCount++;
                previousLine = currentLine;
            }

            // Add the last change block if any existA
            if (startLine != null) {
                changeBlocks.add(new GroupedFile.ChangeBlock(IntStream.range(startLine, startLine + lineCount).boxed().toList()));
            }

            if (!changeBlocks.equals(groupedFile.getCommonChanges())) {
                groupedFile.setCommonChanges(changeBlocks);
                didChanges = true;
            }
        }

        return didChanges;
    }
}
