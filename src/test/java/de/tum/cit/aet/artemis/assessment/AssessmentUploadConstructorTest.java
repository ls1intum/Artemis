package de.tum.cit.aet.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.servlet.autoconfigure.MultipartProperties;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentUploadErrorType;
import de.tum.cit.aet.artemis.assessment.dto.AssessmentUploadErrorDTO;
import de.tum.cit.aet.artemis.assessment.dto.AssessmentUploadResultDTO;
import de.tum.cit.aet.artemis.assessment.repository.AssessmentUploadParticipationRepository;
import de.tum.cit.aet.artemis.assessment.service.AssessmentUploadResultService;
import de.tum.cit.aet.artemis.assessment.service.AssessmentUploadService;
import de.tum.cit.aet.artemis.assessment.web.AssessmentUploadResource;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

class AssessmentUploadConstructorTest {

    @Test
    void shouldRejectNullAssessmentUploadResourceDependencies() {
        final AssessmentUploadService assessmentUploadService = mock(AssessmentUploadService.class);
        final ProgrammingExerciseRepository programmingExerciseRepository = mock(ProgrammingExerciseRepository.class);
        final MultipartProperties multipartProperties = new MultipartProperties();

        assertThatIllegalArgumentException().isThrownBy(() -> new AssessmentUploadResource(null, programmingExerciseRepository, multipartProperties));
        assertThatIllegalArgumentException().isThrownBy(() -> new AssessmentUploadResource(assessmentUploadService, null, multipartProperties));
        assertThatIllegalArgumentException().isThrownBy(() -> new AssessmentUploadResource(assessmentUploadService, programmingExerciseRepository, null));
    }

    @Test
    void shouldRejectNullAssessmentUploadServiceDependencies() {
        final AssessmentUploadParticipationRepository assessmentUploadParticipationRepository = mock(AssessmentUploadParticipationRepository.class);
        final SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
        final AssessmentUploadResultService assessmentUploadResultService = mock(AssessmentUploadResultService.class);

        assertThatIllegalArgumentException().isThrownBy(() -> new AssessmentUploadService(null, submissionRepository, assessmentUploadResultService));
        assertThatIllegalArgumentException().isThrownBy(() -> new AssessmentUploadService(assessmentUploadParticipationRepository, null, assessmentUploadResultService));
        assertThatIllegalArgumentException().isThrownBy(() -> new AssessmentUploadService(assessmentUploadParticipationRepository, submissionRepository, null));
    }

    @Test
    void shouldRejectNullAssessmentUploadResultServiceDependencies() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AssessmentUploadResultService(null, null, null, null, null, null, null, null, null, null, null));
    }

    @Test
    void shouldRejectInvalidAssessmentUploadError() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AssessmentUploadErrorDTO(null, null, null));
    }

    @Test
    void shouldRejectInvalidAssessmentUploadResult() {
        final AssessmentUploadErrorDTO error = AssessmentUploadErrorDTO.of(AssessmentUploadErrorType.EMPTY_CSV);

        assertThatIllegalArgumentException().isThrownBy(() -> new AssessmentUploadResultDTO(0, null, List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new AssessmentUploadResultDTO(0, List.of(), null));
        assertThatIllegalArgumentException().isThrownBy(() -> new AssessmentUploadResultDTO(1, List.of(), List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new AssessmentUploadResultDTO(1, List.of("student"), List.of(error)));
        assertThatIllegalArgumentException().isThrownBy(() -> AssessmentUploadResultDTO.success(null));
        assertThatIllegalArgumentException().isThrownBy(() -> AssessmentUploadResultDTO.failure(null));
        assertThatIllegalArgumentException().isThrownBy(() -> AssessmentUploadResultDTO.failure(List.of()));
    }
}
