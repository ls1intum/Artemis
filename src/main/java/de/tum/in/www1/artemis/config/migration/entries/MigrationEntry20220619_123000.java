package de.tum.in.www1.artemis.config.migration.entries;

import java.lang.reflect.Field;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;

import de.tum.in.www1.artemis.config.migration.MigrationEntry;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;

/**
 * This migration extracts the information about a programming result and stores it inside the new variables so the result String can still be displayed correctly in the client
 */
@Component
public class MigrationEntry20220619_123000 extends MigrationEntry {

    private static final Logger LOGGER = LoggerFactory.getLogger(MigrationEntry20220619_123000.class);

    private final ResultRepository resultRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    public MigrationEntry20220619_123000(ResultRepository resultRepository, ProgrammingExerciseRepository programmingExerciseRepository) {
        this.resultRepository = resultRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
    }

    /**
     * Retrieves all results from the database and executes the processing method in batches of 100 results to prevent database timeouts
     */
    @Override
    public void execute() {
        Field resultString;
        try {
            resultString = Result.class.getDeclaredField("resultString");
            resultString.setAccessible(true);
        }
        catch (NoSuchFieldException e) {
            // The migration was already successful and the deprecated field is removed
            return;
        }

        List<ProgrammingExercise> programmingExercises = programmingExerciseRepository.findAll();
        LOGGER.info("Found {} programming exercise to process.", programmingExercises.size());
        long numberOfUnprocessedResults = 0;
        ZonedDateTime latestUnprocessedResultDate = null;

        for (ProgrammingExercise exercise : programmingExercises) {
            List<Result> results = resultRepository.findAllByExerciseId(exercise.getId());
            results.removeIf(result -> getResultString(result, resultString) == null);
            LOGGER.info("Found {} results for the exercise with ID {} to process.", results.size(), exercise.getId());

            for (List<Result> resultList : Lists.partition(results, 100)) {
                LOGGER.info("Process (next) 100 results for the migration in one batch...");
                List<Result> unprocessedResults = processProgrammingResults(resultList, resultString);
                numberOfUnprocessedResults += unprocessedResults.size();
                latestUnprocessedResultDate = (Stream.concat(unprocessedResults.stream().map(Result::getCompletionDate), Stream.of(latestUnprocessedResultDate)))
                        .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
            }
        }

        if (numberOfUnprocessedResults != 0) {
            LOGGER.error(
                    "Found {} results of programming exercises that could not be processed! These results will no longer show a correct resultString! The actual score is unaffected by this.",
                    numberOfUnprocessedResults);
            if (latestUnprocessedResultDate != null) {
                LOGGER.error("The latest of these unprocessed results is from {}.", latestUnprocessedResultDate);
            }
        }
    }

    /*
     * Takes a result string such as "21 of 42 passed, 42 issues" and splits it into its different components: The number of tests, the number of passed tests and the number of
     * issues.
     * @param results Batch of results that should get processed
     */
    private List<Result> processProgrammingResults(List<Result> results, Field resultString) {
        List<Result> unsuccessfulResults = results.stream().filter(result -> {
            String[] resultStringParts = getResultString(result, resultString).split(", ");
            boolean unsuccessful = true;
            for (String resultStringPart : resultStringParts) {
                // Matches e.g. "21 of 42 passed"
                if (resultStringPart.matches(".*of.*passed.*")) {
                    String[] testCaseParts = resultStringPart.split(" ");
                    int passedTestCasesAmount = Integer.parseInt(testCaseParts[0]);
                    int testCasesAmount = Integer.parseInt(testCaseParts[2]);

                    result.setPassedTestCaseCount(passedTestCasesAmount);
                    result.setTestCaseCount(testCasesAmount);

                    // If we found the test cases, we successfully migrated that result
                    unsuccessful = false;
                }
                // Matches e.g. "9 issues"
                else if (resultStringPart.contains("issue")) {
                    String[] issueParts = resultStringPart.split(" ");
                    int codeIssueCount = Integer.parseInt(issueParts[0]);

                    result.setCodeIssueCount(codeIssueCount);
                }
            }
            return unsuccessful;
        }).toList();

        resultRepository.saveAll(results);

        return unsuccessfulResults;
    }

    private String getResultString(Result result, Field resultString) {
        try {
            return (String) resultString.get(result);
        }
        catch (IllegalAccessException e) {
            throw new IllegalStateException("Could not access the field \"resultString\" of the class Result during the migration even though it was not removed yet!");
        }
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
