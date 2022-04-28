package de.tum.in.www1.artemis.service.hestia.behavioral.knowledgesource;

import java.util.TreeSet;
import java.util.regex.Pattern;

import de.tum.in.www1.artemis.service.hestia.behavioral.BehavioralBlackboard;
import de.tum.in.www1.artemis.service.hestia.behavioral.GroupedFile;
import de.tum.in.www1.artemis.service.hestia.behavioral.GroupedFile.ChangeBlock;

/**
 * For each {@link GroupedFile}:
 * There are certain lines that are not covered by Jacoco (which is used for the testwise coverage) that may still be
 * relevant to the code. This includes for example the `else` expression and curly braces.
 * For each {@link ChangeBlock}:
 * Check if there are such lines before or after the ChangeBlock. If there are add the entire prefix/postfix as a
 * potential ChangeBlock to the GroupedFile.
 */
public class AddUncoveredLinesAsPotentialCodeBlocks extends BehavioralKnowledgeSource {

    private static final Pattern CURLY_BRACES_PATTERN = Pattern.compile("\\s*[}{]\\s*");

    private static final Pattern ELSE_PATTERN = Pattern.compile("\\s*}?\\s*else\\s*\\{?\\s*");

    private static final Pattern EMPTY_LINE_PATTERN = Pattern.compile("\\s*");

    public AddUncoveredLinesAsPotentialCodeBlocks(BehavioralBlackboard blackboard) {
        super(blackboard);
    }

    @Override
    public boolean executeCondition() {
        return blackboard.getGroupedFiles() != null && blackboard.getGroupedFiles().stream().noneMatch(groupedFile -> groupedFile.getFileContent() == null)
                && blackboard.getGroupedFiles().stream().noneMatch(groupedFile -> groupedFile.getCommonChanges() == null);
    }

    @Override
    public boolean executeAction() {
        boolean didChanges = false;

        for (GroupedFile groupedFile : blackboard.getGroupedFiles()) {
            var newChangeBlocks = new TreeSet<ChangeBlock>();

            for (ChangeBlock commonChange : groupedFile.getCommonChanges()) {
                var firstLine = commonChange.getLines().first();
                var potentialPrefix = getPotentialPrefix(firstLine, groupedFile.getFileContent());
                if (potentialPrefix != null) {
                    newChangeBlocks.add(potentialPrefix);
                }
                var lastLine = commonChange.getLines().last();
                var potentialPostfix = getPotentialPostfix(lastLine, groupedFile.getFileContent());
                if (potentialPostfix != null) {
                    newChangeBlocks.add(potentialPostfix);
                }
            }

            if (!newChangeBlocks.isEmpty()) {
                groupedFile.getCommonChanges().addAll(newChangeBlocks);
                didChanges = true;
            }
        }

        return didChanges;
    }

    private ChangeBlock getPotentialPrefix(int firstLine, String fileContent) {
        var potentialLines = new TreeSet<Integer>();
        var lineContents = fileContent.split("\n");
        // Starting two lines before the first line, as first line is the line in the file which starts at 1 and not 0
        for (int i = firstLine - 2; i >= 0; i--) {
            var lineContent = lineContents[i];
            if (doesLineMatch(lineContent)) {
                potentialLines.add(i + 1);
            }
            else {
                break;
            }
        }
        if (potentialLines.isEmpty()) {
            return null;
        }
        else {
            return new ChangeBlock(potentialLines, true);
        }
    }

    private ChangeBlock getPotentialPostfix(int lastLine, String fileContent) {
        var potentialLines = new TreeSet<Integer>();
        var lineContents = fileContent.split("\n");
        // Starting at last line instead of lastLine + 1, as last line is the line in the file which starts at 1 and not 0
        for (int i = lastLine; i < lineContents.length; i++) {
            var lineContent = lineContents[i];
            if (doesLineMatch(lineContent)) {
                potentialLines.add(i + 1);
            }
            else {
                break;
            }
        }
        if (potentialLines.isEmpty()) {
            return null;
        }
        else {
            return new ChangeBlock(potentialLines, true);
        }
    }

    private boolean doesLineMatch(String lineContent) {
        return CURLY_BRACES_PATTERN.matcher(lineContent).matches() || ELSE_PATTERN.matcher(lineContent).matches() || EMPTY_LINE_PATTERN.matcher(lineContent).matches();
    }
}
