package de.tum.in.www1.artemis.service.hestia.behavioral;

import java.util.*;
import java.util.stream.Collectors;

import de.tum.in.www1.artemis.domain.hestia.CoverageFileReport;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseGitDiffEntry;
import de.tum.in.www1.artemis.domain.hestia.TestwiseCoverageReportEntry;

class GroupGitDiffAndCoverageEntriesByFilePath extends BehavioralKnowledgeSource {

    public GroupGitDiffAndCoverageEntriesByFilePath(BehavioralBlackboard blackboard) {
        super(blackboard);
    }

    @Override
    public boolean executeCondition() {
        return blackboard.getGroupedFiles() == null;
    }

    @Override
    public boolean executeAction() {
        var gitDiffEntriesPerFile = blackboard.getGitDiffReport().getEntries().stream()
                .collect(Collectors.toMap(ProgrammingExerciseGitDiffEntry::getFilePath, Collections::singleton, (set1, set2) -> {
                    var entries = new HashSet<>(set1);
                    entries.addAll(set2);
                    return entries;
                }));
        var coverageEntriesPerFile = blackboard.getCoverageReport().getFileReports().stream()
                .collect(Collectors.toMap(CoverageFileReport::getFilePath, CoverageFileReport::getTestwiseCoverageEntries, (set1, set2) -> {
                    var entries = new HashSet<>(set1);
                    entries.addAll(set2);
                    return entries;
                }));

        var commonFilePaths = new TreeSet<>(gitDiffEntriesPerFile.keySet());
        commonFilePaths.retainAll(coverageEntriesPerFile.keySet());

        List<GroupedFile> groupedFiles = createGroupedFiles(gitDiffEntriesPerFile, coverageEntriesPerFile, commonFilePaths);
        if (groupedFiles.isEmpty()) {
            return false;
        }
        else {
            blackboard.setGroupedFiles(groupedFiles);
            return true;
        }
    }

    private List<GroupedFile> createGroupedFiles(Map<String, Set<ProgrammingExerciseGitDiffEntry>> gitDiffEntriesPerFile,
            Map<String, Set<TestwiseCoverageReportEntry>> coverageEntriesPerFile, TreeSet<String> commonFilePaths) {
        return commonFilePaths.stream().flatMap(filePath -> {
            var gitDiffEntries = gitDiffEntriesPerFile.get(filePath);
            var coverageReportEntries = coverageEntriesPerFile.get(filePath);
            return coverageReportEntries.stream().collect(Collectors.toMap(TestwiseCoverageReportEntry::getTestCase, Collections::singleton, (set1, set2) -> {
                var entries = new HashSet<>(set1);
                entries.addAll(set2);
                return entries;
            })).entrySet().stream().map(entry -> new GroupedFile(filePath, entry.getKey(), gitDiffEntries, entry.getValue()));
        }).toList();
    }

}
