package de.tum.cit.aet.artemis.plagiarism.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismResult;

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
}
