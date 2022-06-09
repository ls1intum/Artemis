package de.tum.in.www1.artemis.config.migration.entries;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;

import de.tum.in.www1.artemis.config.migration.MigrationEntry;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.repository.ResultRepository;

/**
 * This migration extracts the information about a programming result and stores it inside the new variables
 */
@Component
public class MigrationEntry20220608_194500 extends MigrationEntry {

    private static final Logger LOGGER = LoggerFactory.getLogger(MigrationEntry20220608_194500.class);

    private final ResultRepository resultRepository;

    public MigrationEntry20220608_194500(ResultRepository resultRepository) {
        this.resultRepository = resultRepository;
    }

    /**
     * Retrieves all results from the database and executes the processing method in batches of 100 results to prevent database timeouts
     */
    @Override
    public void execute() {
        List<Result> results = resultRepository.findAll();

        results.removeIf(result -> result.getResultString() == null || !result.getResultString().matches(".*of.*passed.*"));

        LOGGER.info("Found {} results to process.", results.size());

        Lists.partition(results, 100).forEach(resultList -> {
            LOGGER.info("Process (next) 100 results for the migration in one batch...");
            processResults(resultList);
        });
    }

    /*
     * Takes a result string such as "21 of 42 passed, 42 issues" and splits it into its different components: The number of tests, the number of passed tests and the number of
     * issues.
     * @param results Batch of results that should get processed
     */
    private void processResults(List<Result> results) {
        results.forEach(result -> {
            String[] resultStringParts = result.getResultString().split(", ");
            for (String resultStringPart : resultStringParts) {
                // Matches e.g. "21 of 42 passed"
                if (resultStringPart.matches(".*of.*passed.*")) {
                    String[] testCaseParts = resultStringPart.split(" ");
                    int passedTestCasesAmount = Integer.parseInt(testCaseParts[0]);
                    int testCasesAmount = Integer.parseInt(testCaseParts[2]);

                    result.setPassedTestCaseCount(passedTestCasesAmount);
                    result.setTestCaseCount(testCasesAmount);
                    result.setResultString(null);
                }
                // Matches e.g. "9 issues"
                else if (resultStringPart.contains("issue")) {
                    String[] issueParts = resultStringPart.split(" ");
                    int codeIssueCount = Integer.parseInt(issueParts[0]);

                    result.setCodeIssueCount(codeIssueCount);
                    result.setResultString(null);
                }
            }
        });

        resultRepository.saveAll(results);
    }

    /**
     * @return Author of the entry. Either full name or GitHub name.
     */
    @Override
    public String author() {
        return "johannes-stöhr";
    }

    /**
     * Format YYYYMMDD_HHmmss
     *
     * @return Current time in given format
     */
    @Override
    public String date() {
        return "20220608_194500";
    }
}
