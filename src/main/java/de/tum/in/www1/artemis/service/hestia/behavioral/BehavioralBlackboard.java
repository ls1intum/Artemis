package de.tum.in.www1.artemis.service.hestia.behavioral;

import java.util.*;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.domain.hestia.CoverageReport;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseGitDiffReport;

class BehavioralBlackboard {

    private final ProgrammingExercise programmingExercise;

    private final Set<ProgrammingExerciseTestCase> testCases;

    private final ProgrammingExerciseGitDiffReport gitDiffReport;

    private final CoverageReport coverageReport;

    private final Map<String, String> solutionRepoFiles;

    private List<GroupedFile> groupedFiles;

    private List<BehavioralSolutionEntry> solutionEntries;

    public BehavioralBlackboard(ProgrammingExercise programmingExercise, Set<ProgrammingExerciseTestCase> testCases, ProgrammingExerciseGitDiffReport gitDiffReport,
            CoverageReport coverageReport, Map<String, String> solutionRepoFiles) {
        this.programmingExercise = programmingExercise;
        this.testCases = testCases;
        this.gitDiffReport = gitDiffReport;
        this.coverageReport = coverageReport;
        this.solutionRepoFiles = solutionRepoFiles;
    }

    public ProgrammingExercise getProgrammingExercise() {
        return programmingExercise;
    }

    public Set<ProgrammingExerciseTestCase> getTestCases() {
        return testCases;
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

    public List<BehavioralSolutionEntry> getSolutionEntries() {
        return solutionEntries;
    }

    public void setSolutionEntries(List<BehavioralSolutionEntry> solutionEntries) {
        this.solutionEntries = solutionEntries;
    }

    public List<GroupedFile> getGroupedFiles() {
        return groupedFiles;
    }

    public void setGroupedFiles(List<GroupedFile> groupedFiles) {
        this.groupedFiles = groupedFiles;
    }
}
