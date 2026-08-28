package de.tum.cit.aet.artemis.assessment;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.boot.servlet.autoconfigure.MultipartProperties;
import org.springframework.transaction.PlatformTransactionManager;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentUploadErrorType;
import de.tum.cit.aet.artemis.assessment.dto.AssessmentUploadErrorDTO;
import de.tum.cit.aet.artemis.assessment.dto.AssessmentUploadResultDTO;
import de.tum.cit.aet.artemis.assessment.repository.AssessmentUploadParticipationRepository;
import de.tum.cit.aet.artemis.assessment.repository.AssessmentUploadResultRepository;
import de.tum.cit.aet.artemis.assessment.service.AssessmentUploadArchiveParsingService;
import de.tum.cit.aet.artemis.assessment.service.AssessmentUploadResultService;
import de.tum.cit.aet.artemis.assessment.service.AssessmentUploadService;
import de.tum.cit.aet.artemis.assessment.web.AssessmentUploadResource;
import de.tum.cit.aet.artemis.assessment.web.ResultWebsocketService;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.exercise.service.SubmissionService;
import de.tum.cit.aet.artemis.lti.api.LtiApi;
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
        final AssessmentUploadArchiveParsingService archiveParser = mock(AssessmentUploadArchiveParsingService.class);
        final AssessmentUploadParticipationRepository assessmentUploadParticipationRepository = mock(AssessmentUploadParticipationRepository.class);
        final SubmissionRepository submissionRepository = mock(SubmissionRepository.class);
        final AssessmentUploadResultService assessmentUploadResultService = mock(AssessmentUploadResultService.class);
        final SubmissionService submissionService = mock(SubmissionService.class);
        final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);

        assertThatIllegalArgumentException().isThrownBy(() -> new AssessmentUploadService(null, assessmentUploadParticipationRepository, submissionRepository,
                assessmentUploadResultService, submissionService, transactionManager));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AssessmentUploadService(archiveParser, null, submissionRepository, assessmentUploadResultService, submissionService, transactionManager));
        assertThatIllegalArgumentException().isThrownBy(() -> new AssessmentUploadService(archiveParser, assessmentUploadParticipationRepository, null,
                assessmentUploadResultService, submissionService, transactionManager));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new AssessmentUploadService(archiveParser, assessmentUploadParticipationRepository, submissionRepository, null, submissionService, transactionManager));
        assertThatIllegalArgumentException().isThrownBy(() -> new AssessmentUploadService(archiveParser, assessmentUploadParticipationRepository, submissionRepository,
                assessmentUploadResultService, null, transactionManager));
        assertThatIllegalArgumentException().isThrownBy(() -> new AssessmentUploadService(archiveParser, assessmentUploadParticipationRepository, submissionRepository,
                assessmentUploadResultService, submissionService, null));
    }

    @Test
    void shouldRejectNullAssessmentUploadResultServiceDependencies() {
        final Object[] dependencies = { mock(UserRepository.class), mock(AssessmentUploadResultRepository.class), Optional.<LtiApi>empty(), mock(ResultWebsocketService.class),
                mock(SubmissionRepository.class) };

        for (int dependencyIndex = 0; dependencyIndex < dependencies.length; dependencyIndex++) {
            final Object[] dependenciesWithNull = dependencies.clone();
            dependenciesWithNull[dependencyIndex] = null;
            assertThatIllegalArgumentException().isThrownBy(() -> createAssessmentUploadResultService(dependenciesWithNull));
        }
    }

    @SuppressWarnings("unchecked")
    private AssessmentUploadResultService createAssessmentUploadResultService(final Object[] dependencies) {
        return new AssessmentUploadResultService((UserRepository) dependencies[0], (AssessmentUploadResultRepository) dependencies[1], (Optional<LtiApi>) dependencies[2],
                (ResultWebsocketService) dependencies[3], (SubmissionRepository) dependencies[4]);
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
