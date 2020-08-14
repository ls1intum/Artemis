package de.tum.in.www1.artemis.service.compass.umlmodel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.assertj.core.data.Offset;

import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.service.plagiarism.ModelingPlagiarismDetectionService;

public abstract class AbstractUMLDiagramTest {

    protected ModelingPlagiarismDetectionService modelingPlagiarismDetectionService = new ModelingPlagiarismDetectionService();

    protected void compareSubmissions(ModelingSubmission modelingSubmission1, ModelingSubmission modelingSubmission2, double minimumSimilarity, double expectedSimilarity) {
        // not really necessary, but avoids issues.
        modelingSubmission1.setId(1L);
        modelingSubmission2.setId(2L);

        var comparisonResult = modelingPlagiarismDetectionService.compareSubmissions(List.of(modelingSubmission1, modelingSubmission2), minimumSimilarity);
        assertThat(comparisonResult).isNotNull();
        assertThat(comparisonResult).hasSize(1);
        assertThat(comparisonResult.get(0).getSimilarity()).isEqualTo(expectedSimilarity, Offset.offset(0.01));
    }
}
