package de.tum.in.www1.artemis.web.rest.plagiarism;

import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismComparison;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismSubmission;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextPlagiarismResult;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextSubmissionElement;

class PlagiarismResultResponseBuilderTest {

    @Test
    void shouldReturnEmptyResponseForNullResult() {
        // when
        var response = PlagiarismResultResponseBuilder.buildPlagiarismResultResponse(null);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void shouldReturnCorrectResponseForManualChecks() {
        // given
        var plagiarismResult = createPlagiarismResult("user abc");

        // when
        var response = PlagiarismResultResponseBuilder.buildPlagiarismResultResponse(plagiarismResult);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().plagiarismResult()).isSameAs(plagiarismResult);
        assertThat(response.getBody().plagiarismResultStats().numberOfDetectedSubmissions()).isEqualTo(2);
        assertThat(response.getBody().plagiarismResultStats().averageSimilarity()).isEqualTo(0.78);
        assertThat(response.getBody().plagiarismResultStats().maximalSimilarity()).isEqualTo(0.78);
        assertThat(response.getBody().plagiarismResultStats().createdBy()).isEqualTo("user abc");
    }

    @Test
    void shouldReturnCorrectResponseForCpcChecks() {
        // given
        var plagiarismResult = createPlagiarismResult("system");

        // when
        var response = PlagiarismResultResponseBuilder.buildPlagiarismResultResponse(plagiarismResult);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().plagiarismResult()).isSameAs(plagiarismResult);
        assertThat(response.getBody().plagiarismResultStats().numberOfDetectedSubmissions()).isEqualTo(2);
        assertThat(response.getBody().plagiarismResultStats().averageSimilarity()).isEqualTo(0.78);
        assertThat(response.getBody().plagiarismResultStats().maximalSimilarity()).isEqualTo(0.78);
        assertThat(response.getBody().plagiarismResultStats().createdBy()).isEqualTo("CPC");
    }

    private static TextPlagiarismResult createPlagiarismResult(String system) {
        var submissionA = new PlagiarismSubmission<>();
        submissionA.setId(1L);
        var submissionB = new PlagiarismSubmission<>();
        submissionB.setId(2L);

        var comparison = new PlagiarismComparison<TextSubmissionElement>();
        comparison.setSimilarity(0.78);
        comparison.setSubmissionA(submissionA);
        comparison.setSubmissionB(submissionB);

        var plagiarismResult = new TextPlagiarismResult();
        plagiarismResult.setComparisons(singleton(comparison));
        plagiarismResult.setCreatedBy(system);
        return plagiarismResult;
    }

}
