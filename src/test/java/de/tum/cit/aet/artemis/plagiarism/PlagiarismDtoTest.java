package de.tum.cit.aet.artemis.plagiarism;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismResult;
import de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismCaseDetailDTO;
import de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismCaseInfoDTO;
import de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismCaseOverviewDTO;
import de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismMatchDTO;
import de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismResultDetailsDTO;

class PlagiarismDtoTest {

    @Test
    void detailConstructorHandlesPartialNamesAndLargeSubmissionCounts() {
        var dto = new PlagiarismCaseDetailDTO(1L, null, 2L, "student", null, "Student", null, null, null, null, 3L, "instructor", "Instructor", null, Long.MAX_VALUE, false, null,
                0);

        assertThat(dto.student().name()).isEqualTo("Student");
        assertThat(dto.verdictBy().name()).isEqualTo("Instructor");
        assertThat(dto.plagiarismSubmissionCount()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void overviewConstructorHandlesPartialNamesAndLargeSubmissionCounts() {
        var dto = new PlagiarismCaseOverviewDTO(1L, null, 2L, "student", "Student", null, null, null, false, null, null, 3L, "instructor", null, "Instructor", Long.MAX_VALUE,
                false);

        assertThat(dto.student().name()).isEqualTo("Student");
        assertThat(dto.verdictBy().name()).isEqualTo("Instructor");
        assertThat(dto.plagiarismSubmissionCount()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void resultDetailsKeepsMissingSimilarityDistributionNull() {
        var dto = PlagiarismResultDetailsDTO.fromResult(new PlagiarismResult());

        assertThat(dto.similarityDistribution()).isNull();
    }

    /**
     * The OpenAPI specification marks these primitive components as required. {@code NON_EMPTY} must therefore keep {@code 0} and {@code false} in the JSON.
     */
    @Test
    void requiredPrimitivesSurviveNonEmptyInclusion() {
        var mapper = new JsonMapper();

        String detail = mapper
                .writeValueAsString(new PlagiarismCaseDetailDTO(1L, null, null, null, null, null, null, null, null, null, null, null, null, null, 0L, false, null, 0));
        assertThat(detail).contains("\"plagiarismSubmissionCount\":0", "\"createdByContinuousPlagiarismControl\":false", "\"verdictPointDeduction\":0");

        String overview = mapper
                .writeValueAsString(new PlagiarismCaseOverviewDTO(1L, null, null, null, null, null, null, null, false, null, null, null, null, null, null, 0L, false));
        assertThat(overview).contains("\"plagiarismSubmissionCount\":0", "\"createdByContinuousPlagiarismControl\":false", "\"hasStudentAnswer\":false");

        assertThat(mapper.writeValueAsString(new PlagiarismCaseInfoDTO(1L, null, false))).contains("\"createdByContinuousPlagiarismControl\":false");
        assertThat(mapper.writeValueAsString(new PlagiarismMatchDTO(0, 0, 0))).contains("\"startA\":0", "\"startB\":0", "\"length\":0");
    }
}
