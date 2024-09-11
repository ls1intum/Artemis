package de.tum.cit.aet.artemis.service.hestia.behavioral;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.hestia.ProgrammingExerciseGitDiffEntry;
import de.tum.cit.aet.artemis.programming.domain.hestia.TestwiseCoverageReportEntry;

/**
 * One GroupedFile groups the {@link ProgrammingExerciseGitDiffEntry}s and {@link TestwiseCoverageReportEntry}s together
 * that belong to the same file. For each {@link ProgrammingExerciseTestCase} that covered the file a separate GroupedFile exists.
 */
public class GroupedFile {

    // The path of the file
    private final String filePath;

    // The test case that covered something in this file
    private final ProgrammingExerciseTestCase testCase;

    // All changes between the template and solution repositories in this file
    private final Set<ProgrammingExerciseGitDiffEntry> gitDiffEntries;

    // All coverage entries of the test case in this file
    private final Set<TestwiseCoverageReportEntry> coverageReportEntries;

    // The content of this file
    private String fileContent;

    // The lines of this file that were changed
    private Set<Integer> changedLines;

    // The lines of this file that were covered by the test case
    private Set<Integer> coveredLines;

    // The lines in this file that were both covered and changed
    private SortedSet<Integer> commonLines;

    // The changes in this file that should be included in the solution entries
    private SortedSet<ChangeBlock> commonChanges;

    public GroupedFile(String filePath, ProgrammingExerciseTestCase testCase, Set<ProgrammingExerciseGitDiffEntry> gitDiffEntries,
            Set<TestwiseCoverageReportEntry> coverageReportEntries) {
        this.filePath = filePath;
        this.testCase = testCase;
        this.gitDiffEntries = gitDiffEntries;
        this.coverageReportEntries = coverageReportEntries;
    }

    public String getFilePath() {
        return filePath;
    }

    public ProgrammingExerciseTestCase getTestCase() {
        return testCase;
    }

    public Set<ProgrammingExerciseGitDiffEntry> getGitDiffEntries() {
        return gitDiffEntries;
    }

    public Set<TestwiseCoverageReportEntry> getCoverageReportEntries() {
        return coverageReportEntries;
    }

    public String getFileContent() {
        return fileContent;
    }

    public void setFileContent(String fileContent) {
        this.fileContent = fileContent;
    }

    public Set<Integer> getChangedLines() {
        return changedLines;
    }

    public void setChangedLines(Set<Integer> changedLines) {
        this.changedLines = changedLines;
    }

    public Set<Integer> getCoveredLines() {
        return coveredLines;
    }

    public void setCoveredLines(Set<Integer> coveredLines) {
        this.coveredLines = coveredLines;
    }

    public SortedSet<Integer> getCommonLines() {
        return commonLines;
    }

    public void setCommonLines(Collection<Integer> commonLines) {
        this.commonLines = new TreeSet<>(commonLines);
    }

    public SortedSet<ChangeBlock> getCommonChanges() {
        return commonChanges;
    }

    public void setCommonChanges(Collection<ChangeBlock> commonChanges) {
        this.commonChanges = new TreeSet<>(commonChanges);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filePath, testCase, gitDiffEntries, coverageReportEntries, fileContent, changedLines, coveredLines, commonLines, commonChanges);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        GroupedFile that = (GroupedFile) obj;
        return Objects.equals(filePath, that.filePath) && Objects.equals(testCase, that.testCase) && Objects.equals(gitDiffEntries, that.gitDiffEntries)
                && Objects.equals(coverageReportEntries, that.coverageReportEntries) && Objects.equals(fileContent, that.fileContent)
                && Objects.equals(changedLines, that.changedLines) && Objects.equals(coveredLines, that.coveredLines) && Objects.equals(commonLines, that.commonLines)
                && Objects.equals(commonChanges, that.commonChanges);
    }

    public record ChangeBlock(SortedSet<Integer> lines, boolean isPotential) implements Comparable<ChangeBlock> {

        public ChangeBlock(Collection<Integer> lines) {
            this(new TreeSet<>(lines), false);
        }

        public ChangeBlock(Collection<Integer> lines, boolean isPotential) {
            this(new TreeSet<>(lines), isPotential);
        }

        @Override
        public int compareTo(GroupedFile.ChangeBlock other) {
            return Integer.compare(this.lines.first(), other.lines.first());
        }

        public boolean intersectsOrTouches(ChangeBlock other) {
            return (this.lines.first() > other.lines.first() && this.lines.first() <= other.lines.last() + 1)
                    || (this.lines.first() < other.lines.first() && this.lines.last() >= other.lines.first() - 1);
        }
    }
}
