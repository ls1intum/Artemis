package de.tum.cit.aet.artemis.plagiarism;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismComparison;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismSubmission;
import de.tum.cit.aet.artemis.plagiarism.domain.text.TextPlagiarismResult;
import de.tum.cit.aet.artemis.plagiarism.domain.text.TextSubmissionElement;
import de.tum.cit.aet.artemis.plagiarism.web.PlagiarismResultResponseBuilder;

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
        var plagiarismResult = createPlagiarismResult();

        // when
        var response = PlagiarismResultResponseBuilder.buildPlagiarismResultResponse(plagiarismResult);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().plagiarismResult()).isSameAs(plagiarismResult);
        assertThat(response.getBody().plagiarismResultStats().numberOfDetectedSubmissions()).isEqualTo(3);
        assertThat(response.getBody().plagiarismResultStats().averageSimilarity()).isEqualTo(0.78);
        assertThat(response.getBody().plagiarismResultStats().maximalSimilarity()).isEqualTo(0.78);
    }

    @Test
    void shouldReturnCorrectResponseForCpcChecks() {
        // given
        var plagiarismResult = createPlagiarismResult();

        // when
        var response = PlagiarismResultResponseBuilder.buildPlagiarismResultResponse(plagiarismResult);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().plagiarismResult()).isSameAs(plagiarismResult);
        assertThat(response.getBody().plagiarismResultStats().numberOfDetectedSubmissions()).isEqualTo(3);
        assertThat(response.getBody().plagiarismResultStats().averageSimilarity()).isEqualTo(0.78);
        assertThat(response.getBody().plagiarismResultStats().maximalSimilarity()).isEqualTo(0.78);
    }

    private static TextPlagiarismResult createPlagiarismResult() {
        var submissionA = new PlagiarismSubmission<>();
        submissionA.setSubmissionId(1L);
        var submissionB = new PlagiarismSubmission<>();
        submissionB.setSubmissionId(2L);
        var submissionC = new PlagiarismSubmission<>();
        submissionC.setSubmissionId(3L);

        var comparison1 = new PlagiarismComparison<TextSubmissionElement>();
        comparison1.setSimilarity(0.78);
        comparison1.setSubmissionA(submissionA);
        comparison1.setSubmissionB(submissionB);

        var comparison2 = new PlagiarismComparison<TextSubmissionElement>();
        comparison2.setSimilarity(0.78);
        comparison2.setSubmissionA(submissionA);
        comparison2.setSubmissionB(submissionC);

        var plagiarismResult = new TextPlagiarismResult();
        plagiarismResult.setComparisons(Set.of(comparison1, comparison2));
        return plagiarismResult;
    }

}
