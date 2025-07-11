package de.tum.cit.aet.artemis.plagiarism.web;

import java.util.stream.DoubleStream;
import java.util.stream.Stream;

import org.springframework.http.ResponseEntity;

import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismComparison;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismResult;
import de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismResultDTO;
import de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismResultStats;

/**
 * A class containing a shared logic for creating an HTTP response about plagiarism checks results
 */
public class PlagiarismResultResponseBuilder {

    private PlagiarismResultResponseBuilder() {
    }

    /**
     * Build an HTTP response about the given plagiarism checks results.
     * This method calculates statistics about the result and returns a response containing both - the result and its statistics.
     *
     * @param plagiarismResult the plagiarism checks result to build the response for
     * @return an HTTP response about the given plagiarism checks results
     */
    public static ResponseEntity<PlagiarismResultDTO> buildPlagiarismResultResponse(PlagiarismResult plagiarismResult) {
        if (plagiarismResult == null) {
            return ResponseEntity.ok(null);
        }

        int numberOfDetectedSubmissions = (int) plagiarismResult.getComparisons().stream()
                .flatMap(comparison -> Stream.of(comparison.getSubmissionA().getSubmissionId(), comparison.getSubmissionB().getSubmissionId())).distinct().count();
        double averageSimilarity = getSimilarities(plagiarismResult).average().orElse(0.0);
        double maximalSimilarity = getSimilarities(plagiarismResult).max().orElse(0.0);
        var stats = new PlagiarismResultStats(numberOfDetectedSubmissions, averageSimilarity, maximalSimilarity, plagiarismResult.getCreatedBy());

        return ResponseEntity.ok(new PlagiarismResultDTO(plagiarismResult, stats));
    }

    private static DoubleStream getSimilarities(PlagiarismResult plagiarismResult) {
        return plagiarismResult.getComparisons().stream().mapToDouble(PlagiarismComparison::getSimilarity);
    }
}
