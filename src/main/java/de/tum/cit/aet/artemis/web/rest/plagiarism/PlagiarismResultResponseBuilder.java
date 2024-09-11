package de.tum.cit.aet.artemis.web.rest.plagiarism;

import static de.tum.cit.aet.artemis.core.config.Constants.SYSTEM_ACCOUNT;

import java.util.Objects;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

import org.springframework.http.ResponseEntity;

import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismComparison;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismResult;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismSubmissionElement;
import de.tum.cit.aet.artemis.web.rest.dto.plagiarism.PlagiarismResultDTO;

/**
 * A class containing a shared logic for creating an HTTP response about plagiarism checks results
 */
public class PlagiarismResultResponseBuilder {

    private static final String CONTINUOUS_PLAGIARISM_CONTROL_CREATED_BY_VALUE = "CPC";

    private PlagiarismResultResponseBuilder() {
    }

    /**
     * Build an HTTP response about the given plagiarism checks results.
     * This method calculates statistics about the result and returns a response containing both - the result and its statistics.
     *
     * @param plagiarismResult the plagiarism checks result to build the response for
     * @return an HTTP response about the given plagiarism checks results
     * @param <E> type of the plagiarism checks result
     */
    public static <E extends PlagiarismResult<? extends PlagiarismSubmissionElement>> ResponseEntity<PlagiarismResultDTO<E>> buildPlagiarismResultResponse(E plagiarismResult) {
        if (plagiarismResult == null) {
            return ResponseEntity.ok(null);
        }

        int numberOfDetectedSubmissions = (int) plagiarismResult.getComparisons().stream()
                .flatMap(comparison -> Stream.of(comparison.getSubmissionA().getSubmissionId(), comparison.getSubmissionB().getSubmissionId())).distinct().count();
        double averageSimilarity = getSimilarities(plagiarismResult).average().orElse(0.0);
        double maximalSimilarity = getSimilarities(plagiarismResult).max().orElse(0.0);
        var createdBy = getCreatedBy(plagiarismResult);
        var stats = new PlagiarismResultStats(numberOfDetectedSubmissions, averageSimilarity, maximalSimilarity, createdBy);

        return ResponseEntity.ok(new PlagiarismResultDTO<>(plagiarismResult, stats));
    }

    private static DoubleStream getSimilarities(PlagiarismResult<?> plagiarismResult) {
        return plagiarismResult.getComparisons().stream().mapToDouble(PlagiarismComparison::getSimilarity);
    }

    private static String getCreatedBy(PlagiarismResult<?> result) {
        if (Objects.equals(result.getCreatedBy(), SYSTEM_ACCOUNT)) {
            return CONTINUOUS_PLAGIARISM_CONTROL_CREATED_BY_VALUE;
        }
        else {
            return result.getCreatedBy();
        }
    }
}
