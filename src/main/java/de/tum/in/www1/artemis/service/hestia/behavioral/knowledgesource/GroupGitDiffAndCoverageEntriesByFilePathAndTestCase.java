package de.tum.in.www1.artemis.service.hestia.behavioral.knowledgesource;

import java.util.*;
import java.util.stream.Collectors;

import de.tum.in.www1.artemis.domain.hestia.CoverageFileReport;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseGitDiffEntry;
import de.tum.in.www1.artemis.domain.hestia.TestwiseCoverageReportEntry;
import de.tum.in.www1.artemis.service.hestia.behavioral.BehavioralBlackboard;
import de.tum.in.www1.artemis.service.hestia.behavioral.GroupedFile;

/**
 * Knowledge source that takes care of creating the {@link GroupedFile}s used by all other knowledge sources.
 * These GroupedFiles are created by grouping all coverage entries and git-diff entries together that belong the same file and test case.
 */
public class GroupGitDiffAndCoverageEntriesByFilePathAndTestCase extends BehavioralKnowledgeSource {

    public GroupGitDiffAndCoverageEntriesByFilePathAndTestCase(BehavioralBlackboard blackboard) {
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
                .collect(Collectors.toMap(CoverageFileReport::getFilePath, CoverageFileReport::getTestwiseCoverageEntries));

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
            Map<String, Set<TestwiseCoverageReportEntry>> coverageEntriesPerFile, SortedSet<String> commonFilePaths) {
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
