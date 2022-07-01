package de.tum.in.www1.artemis.hestia.behavioral;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseGitDiffEntry;
import de.tum.in.www1.artemis.service.hestia.behavioral.BehavioralBlackboard;
import de.tum.in.www1.artemis.service.hestia.behavioral.GroupedFile;
import de.tum.in.www1.artemis.service.hestia.behavioral.knowledgesource.ExtractChangedLines;

class ExtractChangedLinesTest {

    private ExtractChangedLines extractChangedLines;

    private GroupedFile groupedFile;

    private Set<ProgrammingExerciseGitDiffEntry> gitDiffEntries;

    @BeforeEach
    void initBlackboard() {
        BehavioralBlackboard blackboard = new BehavioralBlackboard(null, null, null);
        var groupedFiles = new ArrayList<GroupedFile>();
        blackboard.setGroupedFiles(groupedFiles);
        gitDiffEntries = new HashSet<>();
        groupedFile = new GroupedFile("test.java", null, gitDiffEntries, null);
        groupedFiles.add(groupedFile);

        extractChangedLines = new ExtractChangedLines(blackboard);
    }

    @Test
    void testNoAction() {
        groupedFile.setChangedLines(Collections.emptySet());
        assertThat(extractChangedLines.executeCondition()).isFalse();
    }

    @Test
    void testExtractChangedLines1() {
        var gitDiffEntry = new ProgrammingExerciseGitDiffEntry();
        gitDiffEntry.setStartLine(3);
        gitDiffEntry.setLineCount(2);
        gitDiffEntries.add(gitDiffEntry);

        assertThat(extractChangedLines.executeCondition()).isTrue();
        assertThat(extractChangedLines.executeAction()).isTrue();
        assertThat(groupedFile.getChangedLines()).containsExactly(3, 4);
    }

    @Test
    void testExtractChangedLines2() {
        var gitDiffEntry1 = new ProgrammingExerciseGitDiffEntry();
        gitDiffEntry1.setStartLine(3);
        gitDiffEntry1.setLineCount(2);
        var gitDiffEntry2 = new ProgrammingExerciseGitDiffEntry();
        gitDiffEntry2.setStartLine(6);
        gitDiffEntry2.setLineCount(3);
        gitDiffEntries.add(gitDiffEntry1);
        gitDiffEntries.add(gitDiffEntry2);

        assertThat(extractChangedLines.executeCondition()).isTrue();
        assertThat(extractChangedLines.executeAction()).isTrue();
        assertThat(groupedFile.getChangedLines()).containsExactly(3, 4, 6, 7, 8);
    }
}
