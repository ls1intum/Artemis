package de.tum.in.www1.artemis.web.rest.plagiarism;

import java.util.stream.DoubleStream;
import java.util.stream.Stream;

import org.springframework.http.ResponseEntity;

import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismComparison;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismResult;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismSubmissionElement;
import de.tum.in.www1.artemis.web.rest.TextExerciseResource;
import de.tum.in.www1.artemis.web.rest.dto.plagiarism.PlagiarismResultDTO;

public class PlagiarismResultResponseBuilder {

    private PlagiarismResultResponseBuilder() {
    }

    public static <E extends PlagiarismResult<? extends PlagiarismSubmissionElement>> ResponseEntity<PlagiarismResultDTO<E>> buildPlagiarismResultResponse(E plagiarismResult) {
        if (plagiarismResult == null) {
            return ResponseEntity.ok(null);
        }

        int numberOfDetectedSubmissions = (int) plagiarismResult.getComparisons().stream()
                .flatMap(comparison -> Stream.of(comparison.getSubmissionA().getId(), comparison.getSubmissionB().getId())).distinct().count();
        double averageSimilarity = getSimilarities(plagiarismResult).average().orElse(0.0);
        double maximalSimilarity = getSimilarities(plagiarismResult).max().orElse(0.0);
        var stats = new TextExerciseResource.PlagiarismResultStats(numberOfDetectedSubmissions, averageSimilarity, maximalSimilarity);

        return ResponseEntity.ok(new PlagiarismResultDTO<>(plagiarismResult, stats));
    }

    private static DoubleStream getSimilarities(PlagiarismResult<?> plagiarismResult) {
        return plagiarismResult.getComparisons().stream().mapToDouble(PlagiarismComparison::getSimilarity);
    }
}
