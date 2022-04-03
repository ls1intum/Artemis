package de.tum.in.www1.artemis.service.hestia;

import java.util.*;
import java.util.stream.IntStream;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.hestia.CoverageFileReport;
import de.tum.in.www1.artemis.domain.hestia.CoverageReport;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.repository.hestia.CoverageFileReportRepository;
import de.tum.in.www1.artemis.repository.hestia.CoverageReportRepository;
import de.tum.in.www1.artemis.repository.hestia.TestwiseCoverageReportEntryRepository;
import de.tum.in.www1.artemis.service.RepositoryService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.web.rest.errors.InternalServerErrorException;

/**
 * Service for managing testwise coverage data and interacts with both CoverageReport, CoverageFileReport
 * and TestwiseCoverageReportEntry
 */
@Service
public class TestwiseCoverageService {

    private final Logger log = LoggerFactory.getLogger(TestwiseCoverageService.class);

    private final CoverageReportRepository coverageReportRepository;

    private final CoverageFileReportRepository coverageFileReportRepository;

    private final TestwiseCoverageReportEntryRepository testwiseCoverageReportEntryRepository;

    private final ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final GitService gitService;

    private final RepositoryService repositoryService;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    public TestwiseCoverageService(CoverageReportRepository coverageReportRepository, CoverageFileReportRepository coverageFileReportRepository,
            TestwiseCoverageReportEntryRepository testwiseCoverageReportEntryRepository, ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository,
            ProgrammingSubmissionRepository programmingSubmissionRepository, RepositoryService repositoryService,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository, GitService gitService) {
        this.coverageReportRepository = coverageReportRepository;
        this.coverageFileReportRepository = coverageFileReportRepository;
        this.testwiseCoverageReportEntryRepository = testwiseCoverageReportEntryRepository;
        this.programmingExerciseTestCaseRepository = programmingExerciseTestCaseRepository;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.gitService = gitService;
        this.repositoryService = repositoryService;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
    }

    /**
     * Creates a coverage report from a testwise coverage report.
     * Test case names are resolved to a test case of the given programming exercise, adds this reference to the given
     * entries and saves the entries with the test case reference to the database.
     * In case, no test case could be found for the given name, the report for this test case will not be saved
     * @param fileReportByTestCaseName a map containing the test case name as a key and the file coverage reports without the
     * reference to a test case as a value
     * @param submission the solution programming submission for which the report is updated
     * @param exercise the exercise for which the report should be updated
     */
    public void createTestwiseCoverageReport(Map<String, Set<CoverageFileReport>> fileReportByTestCaseName, ProgrammingExercise exercise, ProgrammingSubmission submission) {
        // If the report already exists, do not create a new report. This is the case if the build plan will be re-run
        boolean reportAlreadyExists = coverageReportRepository.findCoverageReportBySubmissionId(submission.getId()).isPresent();
        if (reportAlreadyExists) {
            return;
        }

        var testCases = programmingExerciseTestCaseRepository.findByExerciseId(exercise.getId());
        var solutionLineCountByFilePath = getLineCountByFilePath(exercise);

        // Save the full report with the test case and submission, but without the individual file reports as they do not have an ID yet
        var fullReport = new CoverageReport();
        fullReport.setSubmission(submission);
        var savedFullReport = coverageReportRepository.save(fullReport);

        // The file reports unique for a file path. the set is used to aggregate multiple file reports from multiple test
        // cases into one file report
        var uniqueFileReports = new HashSet<CoverageFileReport>();

        fileReportByTestCaseName.forEach((testCaseName, fileReports) -> {
            // retrieve the test matching the extracted test case name
            var optionalTestCase = testCases.stream().filter(testCase -> testCaseName.equals(testCase.getTestName())).findFirst();
            if (optionalTestCase.isEmpty()) {
                return;
            }
            var testCase = optionalTestCase.get();

            fileReports.forEach(fileReport -> {
                // If the file does not exist in the solution repository, no file report will be created
                // This is for example the case if the test itself invokes code in a test class
                if (solutionLineCountByFilePath.get(fileReport.getFilePath()) == null) {
                    return;
                }

                // Temporarily save the testwise entries for the current file report as the entries do not have
                // an ID and therefore cause an exception when the file report is saved to the database
                var testwiseEntries = fileReport.getTestwiseCoverageEntries();

                var optionalReportWithSameName = uniqueFileReports.stream().filter(report -> report.getFilePath().equals(fileReport.getFilePath())).findFirst();
                CoverageFileReport savedFileReport;

                if (optionalReportWithSameName.isEmpty()) {
                    // Remove the testwise entries temporarily as they do not have an ID yet
                    fileReport.setTestwiseCoverageEntries(Collections.emptySet());
                    fileReport.setFullReport(savedFullReport);
                    // Save the line count
                    var lineCount = solutionLineCountByFilePath.get(fileReport.getFilePath());
                    fileReport.setLineCount(lineCount);

                    savedFileReport = coverageFileReportRepository.save(fileReport);
                }
                else {
                    var reportWithSameName = optionalReportWithSameName.get();
                    savedFileReport = coverageFileReportRepository.save(reportWithSameName);
                }
                uniqueFileReports.add(savedFileReport);

                // Save all entries for the current file report to the database
                testwiseEntries.forEach(entry -> {
                    entry.setTestCase(testCase);
                    entry.setFileReport(savedFileReport);
                    testwiseCoverageReportEntryRepository.save(entry);
                });
            });
        });

        var updatedFullReport = coverageReportRepository.findByIdElseThrow(savedFullReport.getId());
        // Calculate the unique line count for all file reports and save this value to the database
        var coveredLinesCountByFilePath = calculateAndSaveUniqueLineCountsByFilePath(updatedFullReport);

        // Calculate the aggregated covered line ratio over all files
        double aggregatedCoveredLineRatio = calculateAggregatedLineCoverage(solutionLineCountByFilePath, coveredLinesCountByFilePath);
        updatedFullReport.setCoveredLineRatio(aggregatedCoveredLineRatio);
        coverageReportRepository.save(updatedFullReport);
    }

    /**
     * Returns the line count by file name for all files in the solution repository for the last submission.
     * @param programmingExercise the exercise from which the latest submission to the solution repository will be used
     * @return line count by file name of files in the last submission's solution repository
     */
    private Map<String, Integer> getLineCountByFilePath(ProgrammingExercise programmingExercise) {
        try {
            var solutionParticipation = solutionProgrammingExerciseParticipationRepository.findByProgrammingExerciseIdElseThrow(programmingExercise.getId());
            var solutionRepo = gitService.getOrCheckoutRepository(solutionParticipation.getVcsRepositoryUrl(), true);
            var solutionFiles = repositoryService.getFilesWithContent(solutionRepo);
            var result = new HashMap<String, Integer>();
            solutionFiles.forEach((filePath, value) -> {
                var lineCount = value.split("\n").length + 1;
                result.put(filePath, lineCount);
            });
            return result;
        }
        catch (InterruptedException | GitAPIException e) {
            log.error("Exception while generating testwise coverage report", e);
            throw new InternalServerErrorException("Error while generating testwise coverage report: " + e.getMessage());
        }
    }

    /**
     * Calculates the aggregated covered line ratio for all file reports.
     * @param lineCountByFileName the general line count by file name
     * @param coveredLineCountByFileName the covered line count by file name
     * @return the covered line ratio for all files
     */
    private double calculateAggregatedLineCoverage(Map<String, Integer> lineCountByFileName, Map<String, Integer> coveredLineCountByFileName) {
        var aggregatedLineCount = lineCountByFileName.values().stream().mapToInt(Integer::intValue).sum();
        if (aggregatedLineCount == 0) {
            return 0;
        }
        var aggregatedCoveredLineCount = coveredLineCountByFileName.values().stream().mapToInt(Integer::intValue).sum();

        return aggregatedCoveredLineCount / (double) aggregatedLineCount;
    }

    /**
     * Calculate the unique covered line count for all file reports and save this value to the database for all
     * individual file reports. CoverageFileReports can contain multiple TestwiseCoverageReportEntries referencing
     * the same lines, but referencing a different test case. This mapping is still required, but simple summing may
     * count the same covered lines multiple times.
     * @param coverageReport the report for which the line counts of its file reports should be caluclated and saved
     * @return the number of covered lines by file path
     */
    private Map<String, Integer> calculateAndSaveUniqueLineCountsByFilePath(CoverageReport coverageReport) {
        var report = coverageReportRepository.findCoverageReportByIdWithEagerFileReportsElseThrow(coverageReport.getId());
        var coveredLinesByFilePath = new HashMap<String, Integer>();
        report.getFileReports().forEach(fileReport -> {
            var lineSet = new HashSet<Integer>();
            fileReport.getTestwiseCoverageEntries()
                    .forEach(entry -> lineSet.addAll(IntStream.rangeClosed(entry.getStartLine(), entry.getStartLine() + entry.getLineCount() - 1).boxed().toList()));
            fileReport.setCoveredLineCount(lineSet.size());
            coverageFileReportRepository.save(fileReport);
            coveredLinesByFilePath.put(fileReport.getFilePath(), lineSet.size());
        });
        return coveredLinesByFilePath;
    }

    /**
     * Returns the testwise coverage report for the latest solution submission for a programming exercise without the file reports
     * @param programmingExercise the exercise for which the latest coverage report should be retrieved
     * @return the testwise coverage report for the latest solution submission without the file reports
     */
    public CoverageReport getCoverageReportForLatestSolutionSubmissionFromProgrammingExercise(ProgrammingExercise programmingExercise) {
        var solutionParticipation = solutionProgrammingExerciseParticipationRepository.findByProgrammingExerciseIdElseThrow(programmingExercise.getId());
        var latestSubmissions = programmingSubmissionRepository.findLatestLegalSubmissionForParticipation(solutionParticipation.getId(), Pageable.ofSize(1));
        var latestSubmission = latestSubmissions.get(0);
        return coverageReportRepository.findCoverageReportBySubmissionIdElseThrow(latestSubmission.getId());
    }

    /**
     * Returns the full testwise coverage report for the latest solution submission for a programming exercise containing all file reports
     * @param programmingExercise the exercise for which the latest coverage report should be retrieved
     * @return the full testwise coverage report for the latest solution submission with all file reports
     */
    public CoverageReport getFullCoverageReportForLatestSolutionSubmissionFromProgrammingExercise(ProgrammingExercise programmingExercise) {
        var lazyReport = getCoverageReportForLatestSolutionSubmissionFromProgrammingExercise(programmingExercise);
        return coverageReportRepository.findCoverageReportByIdWithEagerFileReportsElseThrow(lazyReport.getId());
    }
}
