package de.tum.in.www1.artemis.service.hestia;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseTestCaseType;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseTestwiseCoverageReport;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.repository.hestia.ProgrammingExerciseTestwiseCoverageReportRepository;
import de.tum.in.www1.artemis.repository.hestia.ProgrammingExercseTestwiseCoverageEntryRepository;

/**
 * Service for managing testwise coverage data and interacts with both ProgrammingExerciseTestwiseCoverageEntry
 * and ProgrammingExerciseTestwiseCoverageReport
 */
@Service
public class ProgrammingExerciseTestwiseCoverageService {

    private ProgrammingExerciseTestwiseCoverageReportRepository programmingExerciseTestwiseCoverageReportRepository;

    private ProgrammingExercseTestwiseCoverageEntryRepository programmingExercseTestwiseCoverageEntryRepository;

    private ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

    public ProgrammingExerciseTestwiseCoverageService(ProgrammingExerciseTestwiseCoverageReportRepository programmingExerciseTestwiseCoverageReportRepository,
            ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository,
            ProgrammingExercseTestwiseCoverageEntryRepository programmingExercseTestwiseCoverageEntryRepository) {
        this.programmingExerciseTestwiseCoverageReportRepository = programmingExerciseTestwiseCoverageReportRepository;
        this.programmingExerciseTestCaseRepository = programmingExerciseTestCaseRepository;
        this.programmingExercseTestwiseCoverageEntryRepository = programmingExercseTestwiseCoverageEntryRepository;
    }

    /**
     * Resolves the test case name to a test case of the given programming exercise, adds this reference to the given
     * reports and saves the reports with the test case reference to the database.
     * In case, no test case could be found for the given name, the report for this test case will not be saved
     * @param reportByTestCaseName a map containing the test case name as a key and the coverage report without the
     * reference to a test case as a value
     * @param exercise the exercise for which the report should be edited and saved
     */
    public void updateReportWithTestCases(Map<String, ProgrammingExerciseTestwiseCoverageReport> reportByTestCaseName, ProgrammingExercise exercise) {
        // reset the old entries
        deleteReportsWithEntriesForProgrammingExercise(exercise);

        var testCases = programmingExerciseTestCaseRepository.findByExerciseId(exercise.getId());

        reportByTestCaseName.forEach((testCaseName, report) -> {
            // retrieve the test matching the extracted test case name
            var optionalTestCase = testCases.stream().filter(testCase -> testCaseName.equals(testCase.getTestName())).findFirst();

            if (optionalTestCase.isEmpty()) {
                return;
            }

            var testCase = optionalTestCase.get();

            // save the report with the test case but without the entries as they do not have an ID yet
            var entries = report.getEntries();
            report.setEntries(new HashSet<>());
            report.setTestCase(testCase);
            var savedReport = programmingExerciseTestwiseCoverageReportRepository.save(report);

            // save the report to the test case
            testCase.setTestwiseCoverageReport(savedReport);
            programmingExerciseTestCaseRepository.save(testCase);

            // save entries after saving the report
            entries.forEach(entry -> entry.setTestwiseCoverageReport(savedReport));
            programmingExercseTestwiseCoverageEntryRepository.saveAll(entries);
        });
    }

    private void deleteReportsWithEntriesForProgrammingExercise(ProgrammingExercise programmingExercise) {
        var testCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());
        testCases.forEach((testCase -> {
            var optionalReport = programmingExerciseTestwiseCoverageReportRepository.findByTestCaseId(testCase.getId());
            // no deletion required if the test case has no coverage report yet
            if (optionalReport.isEmpty()) {
                return;
            }

            var report = optionalReport.get();
            programmingExercseTestwiseCoverageEntryRepository.deleteAll(report.getEntries());
            programmingExerciseTestwiseCoverageReportRepository.delete(report);
            testCase.setTestwiseCoverageReport(null);
            programmingExerciseTestCaseRepository.save(testCase);
        }));
    }

    /**
     * Returns the testwise coverage reports for all active behavioral test cases for a programming exercise
     * @param programmingExercise the exercise for which the active reports should be retrieved
     * @return the testwise coverage reports for all active test cases
     */
    public Set<ProgrammingExerciseTestwiseCoverageReport> getTestwiseCoverageReportsForActiveAndBehaviorTestsForProgrammingExercise(ProgrammingExercise programmingExercise) {
        var tests = programmingExerciseTestCaseRepository.findByExerciseIdAndActive(programmingExercise.getId(), true);
        return tests.stream().filter(test -> ProgrammingExerciseTestCaseType.BEHAVIORAL.equals(test.getType()))
                .map(testCase -> programmingExerciseTestwiseCoverageReportRepository.findByTestCaseIdElseThrow(testCase.getId())).collect(Collectors.toSet());
    }
}
