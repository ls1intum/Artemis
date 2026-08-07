package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.test_repository.ResultTestRepository;
import de.tum.cit.aet.artemis.core.service.ModuleFeatureService;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.dto.ParticipationScoreDTO;
import de.tum.cit.aet.artemis.exercise.dto.ParticipationScoreSearchDTO;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.ParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.SubmissionTestRepository;
import de.tum.cit.aet.artemis.iris.api.IrisSettingsApi;
import de.tum.cit.aet.artemis.iris.domain.askuser.IrisAssessment;
import de.tum.cit.aet.artemis.iris.domain.askuser.IrisVerdict;
import de.tum.cit.aet.artemis.localci.service.ci.ContinuousIntegrationService;
import de.tum.cit.aet.artemis.localvc.service.ParticipationVcsAccessTokenService;
import de.tum.cit.aet.artemis.localvc.service.vcs.VersionControlService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.service.UriService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseStudentParticipationTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * Unit tests for the Iris-assessment branch of {@link ParticipationService#findParticipationScoresForExercise}.
 * Uses plain Mockito mocks (no Spring context) because the behaviour under test is gated by two independently
 * togglable collaborators ({@link ModuleFeatureService#isIrisEnabled()} and an optional {@link IrisSettingsApi}),
 * which is awkward to exercise through the Jenkins/LocalVC integration test stack used by {@link ParticipationServiceTest}.
 */
class ParticipationServiceFindParticipationScoresTest {

    private StudentParticipationTestRepository studentParticipationRepository;

    private ProgrammingExerciseStudentParticipationTestRepository programmingExerciseStudentParticipationRepository;

    private ResultTestRepository resultRepository;

    private ModuleFeatureService moduleFeatureService;

    private IrisSettingsApi irisSettingsApi;

    private ParticipationService participationService;

    private ParticipationScoreSearchDTO search;

    private Pageable pageable;

    @BeforeEach
    void setUp() {
        studentParticipationRepository = mock(StudentParticipationTestRepository.class);
        programmingExerciseStudentParticipationRepository = mock(ProgrammingExerciseStudentParticipationTestRepository.class);
        resultRepository = mock(ResultTestRepository.class);
        moduleFeatureService = mock(ModuleFeatureService.class);
        irisSettingsApi = mock(IrisSettingsApi.class);

        participationService = new ParticipationService(Optional.<ContinuousIntegrationService>empty(), Optional.<VersionControlService>empty(),
                mock(ParticipationTestRepository.class), studentParticipationRepository, programmingExerciseStudentParticipationRepository,
                mock(ProgrammingExerciseTestRepository.class), mock(SubmissionTestRepository.class), mock(TeamRepository.class), mock(UriService.class),
                mock(ParticipationVcsAccessTokenService.class), resultRepository, mock(TemplateProgrammingExerciseParticipationRepository.class), moduleFeatureService,
                Optional.of(irisSettingsApi));

        search = new ParticipationScoreSearchDTO(0, 20, null, null, null, null, null, null);
        pageable = PageRequest.of(0, 20);

        when(resultRepository.findLatestResultsWithAssessmentNoteBySubmissionIds(any())).thenReturn(Set.of());
        when(studentParticipationRepository.countSubmissionsPerParticipationByIdsAsMap(any())).thenReturn(Map.of());
    }

    @Test
    void shouldSkipIrisAssessmentWhenExerciseIsTeamMode() {
        ProgrammingExercise exercise = programmingExercise(1L, ExerciseMode.TEAM);
        ProgrammingExerciseStudentParticipation participation = participationWithoutAssessment(exercise, 10L);

        stubIdPage(exercise, true, List.of(10L));
        when(studentParticipationRepository.findByIdsWithLatestSubmissionWithTeamInformation(List.of(10L))).thenReturn(List.of(participation));

        Page<ParticipationScoreDTO> page = participationService.findParticipationScoresForExercise(exercise, search);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().getFirst().irisAssessment()).isNull();
        verifyNoInteractions(moduleFeatureService);
        verifyNoInteractions(irisSettingsApi);
        verify(programmingExerciseStudentParticipationRepository, never()).findByIdsWithLatestSubmissionAndIrisAssessment(any());
    }

    @Test
    void shouldSkipIrisAssessmentWhenExerciseIsNotProgramming() {
        TextExercise exercise = new TextExercise();
        exercise.setId(2L);
        exercise.setMode(ExerciseMode.INDIVIDUAL);

        stubIdPage(exercise, false, List.of());
        when(studentParticipationRepository.findByIdsWithLatestSubmission(List.of())).thenReturn(List.of());

        participationService.findParticipationScoresForExercise(exercise, search);

        verifyNoInteractions(moduleFeatureService);
        verifyNoInteractions(irisSettingsApi);
        verify(programmingExerciseStudentParticipationRepository, never()).findByIdsWithLatestSubmissionAndIrisAssessment(any());
    }

    @Test
    void shouldSkipIrisAssessmentWhenIrisModuleDisabled() {
        ProgrammingExercise exercise = programmingExercise(3L, ExerciseMode.INDIVIDUAL);
        ProgrammingExerciseStudentParticipation participation = participationWithoutAssessment(exercise, 11L);

        stubIdPage(exercise, false, List.of(11L));
        when(moduleFeatureService.isIrisEnabled()).thenReturn(false);
        when(studentParticipationRepository.findByIdsWithLatestSubmission(List.of(11L))).thenReturn(List.of(participation));

        Page<ParticipationScoreDTO> page = participationService.findParticipationScoresForExercise(exercise, search);

        assertThat(page.getContent().getFirst().irisAssessment()).isNull();
        verifyNoInteractions(irisSettingsApi);
        verify(programmingExerciseStudentParticipationRepository, never()).findByIdsWithLatestSubmissionAndIrisAssessment(any());
    }

    @Test
    void shouldSkipIrisAssessmentWhenAskUserModeDisabledForExercise() {
        ProgrammingExercise exercise = programmingExercise(4L, ExerciseMode.INDIVIDUAL);
        ProgrammingExerciseStudentParticipation participation = participationWithoutAssessment(exercise, 12L);

        stubIdPage(exercise, false, List.of(12L));
        when(moduleFeatureService.isIrisEnabled()).thenReturn(true);
        when(irisSettingsApi.isAskUserModeEnabledForExercise(exercise)).thenReturn(false);
        when(studentParticipationRepository.findByIdsWithLatestSubmission(List.of(12L))).thenReturn(List.of(participation));

        Page<ParticipationScoreDTO> page = participationService.findParticipationScoresForExercise(exercise, search);

        assertThat(page.getContent().getFirst().irisAssessment()).isNull();
        verify(programmingExerciseStudentParticipationRepository, never()).findByIdsWithLatestSubmissionAndIrisAssessment(any());
    }

    @Test
    void shouldMapIrisAssessmentWhenAskUserModeEnabledForProgrammingExercise() {
        ProgrammingExercise exercise = programmingExercise(5L, ExerciseMode.INDIVIDUAL);
        User student = new User();
        student.setId(20L);
        student.setLogin("student1");

        IrisAssessment assessment = new IrisAssessment(student, exercise);
        assessment.setId(99L);
        assessment.setVerdict(IrisVerdict.UNSUSPICIOUS);

        ProgrammingExerciseStudentParticipation participation = new ProgrammingExerciseStudentParticipation();
        participation.setId(13L);
        participation.setExercise(exercise);
        participation.setParticipant(student);
        participation.setIrisAssessment(assessment);

        stubIdPage(exercise, false, List.of(13L));
        when(moduleFeatureService.isIrisEnabled()).thenReturn(true);
        when(irisSettingsApi.isAskUserModeEnabledForExercise(exercise)).thenReturn(true);
        when(programmingExerciseStudentParticipationRepository.findByIdsWithLatestSubmissionAndIrisAssessment(List.of(13L))).thenReturn(List.of(participation));

        Page<ParticipationScoreDTO> page = participationService.findParticipationScoresForExercise(exercise, search);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().getFirst().irisAssessment()).isNotNull();
        assertThat(page.getContent().getFirst().irisAssessment().id()).isEqualTo(99L);
        assertThat(page.getContent().getFirst().irisAssessment().verdict()).isEqualTo(IrisVerdict.UNSUSPICIOUS);
        verify(studentParticipationRepository, never()).findByIdsWithLatestSubmission(any());
    }

    private ProgrammingExercise programmingExercise(long id, ExerciseMode mode) {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(id);
        exercise.setMode(mode);
        return exercise;
    }

    private ProgrammingExerciseStudentParticipation participationWithoutAssessment(ProgrammingExercise exercise, long participationId) {
        ProgrammingExerciseStudentParticipation participation = new ProgrammingExerciseStudentParticipation();
        participation.setId(participationId);
        participation.setExercise(exercise);
        return participation;
    }

    private void stubIdPage(de.tum.cit.aet.artemis.exercise.domain.Exercise exercise, boolean teamMode, List<Long> ids) {
        when(studentParticipationRepository.findParticipationIdsForScores(eq(exercise.getId()), eq(teamMode), eq(search.searchTerm()), eq(search.filterProp()),
                eq(search.scoreRangeLower()), eq(search.scoreRangeUpper()), any(), any(), eq(search.sortedColumn()))).thenReturn(new PageImpl<>(ids, pageable, ids.size()));
    }
}
