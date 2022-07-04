package de.tum.in.www1.artemis.service.hestia.behavioral;

import java.util.*;

import de.tum.in.www1.artemis.domain.hestia.CoverageReport;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseGitDiffReport;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseSolutionEntry;

/**
 * The blackboard for creating SolutionEntries for behavioral test cases utilizing the git-diff and teswise coverage report.
 */
public class BehavioralBlackboard {

    private final ProgrammingExerciseGitDiffReport gitDiffReport;

    private final CoverageReport coverageReport;

    private final Map<String, String> solutionRepoFiles;

    private List<GroupedFile> groupedFiles;

    private List<ProgrammingExerciseSolutionEntry> solutionEntries;

    public BehavioralBlackboard(ProgrammingExerciseGitDiffReport gitDiffReport, CoverageReport coverageReport, Map<String, String> solutionRepoFiles) {
        this.gitDiffReport = gitDiffReport;
        this.coverageReport = coverageReport;
        this.solutionRepoFiles = solutionRepoFiles;
    }

    public ProgrammingExerciseGitDiffReport getGitDiffReport() {
        return gitDiffReport;
    }

    public CoverageReport getCoverageReport() {
        return coverageReport;
    }

    public Map<String, String> getSolutionRepoFiles() {
        return solutionRepoFiles;
    }

    public List<ProgrammingExerciseSolutionEntry> getSolutionEntries() {
        return solutionEntries;
    }

    public void setSolutionEntries(List<ProgrammingExerciseSolutionEntry> solutionEntries) {
        this.solutionEntries = solutionEntries;
    }

    public List<GroupedFile> getGroupedFiles() {
        return groupedFiles;
    }

    public void setGroupedFiles(List<GroupedFile> groupedFiles) {
        this.groupedFiles = groupedFiles;
    }
}
