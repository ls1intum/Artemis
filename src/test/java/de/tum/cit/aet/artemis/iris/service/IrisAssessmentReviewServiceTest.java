package de.tum.cit.aet.artemis.iris.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.TaskScheduler;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.dto.SortingOrder;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenAlertException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.iris.domain.askuser.IrisAssessment;
import de.tum.cit.aet.artemis.iris.domain.askuser.IrisVerdict;
import de.tum.cit.aet.artemis.iris.domain.askuser.IrisVerdictReview;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettings;
import de.tum.cit.aet.artemis.iris.dto.IrisAssessmentProgrammingStudentParticipationDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisAssessmentProgrammingStudentParticipationProjectionDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisAssessmentReviewSearchDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisVerdictDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisAssessmentRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisChatSessionRepository;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisAssessmentQuizWebsocketService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseStudentParticipationTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

class IrisAssessmentReviewServiceTest {

    private IrisAssessmentRepository irisAssessmentRepository;

    private ProgrammingExerciseStudentParticipationTestRepository programmingExerciseStudentParticipationRepository;

    private IrisChatSessionRepository irisChatSessionRepository;

    private StudentParticipationTestRepository studentParticipationRepository;

    private IrisAssessmentQuizWebsocketService irisAssessmentQuizWebsocketService;

    private IrisSettingsService irisSettingsService;

    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    private TaskScheduler taskScheduler;

    private IrisAssessmentReviewService irisAssessmentReviewService;

    @BeforeEach
    void setUp() {
        irisAssessmentRepository = mock(IrisAssessmentRepository.class);
        programmingExerciseStudentParticipationRepository = mock(ProgrammingExerciseStudentParticipationTestRepository.class);
        irisChatSessionRepository = mock(IrisChatSessionRepository.class);
        studentParticipationRepository = mock(StudentParticipationTestRepository.class);
        irisAssessmentQuizWebsocketService = mock(IrisAssessmentQuizWebsocketService.class);
        irisSettingsService = mock(IrisSettingsService.class);
        programmingExerciseRepository = mock(ProgrammingExerciseTestRepository.class);
        taskScheduler = mock(TaskScheduler.class);

        irisAssessmentReviewService = new IrisAssessmentReviewService(irisAssessmentRepository, programmingExerciseStudentParticipationRepository, irisChatSessionRepository,
                studentParticipationRepository, irisAssessmentQuizWebsocketService, irisSettingsService, programmingExerciseRepository, taskScheduler);

        // save(...) on the assessment repository mock returns the same instance it was given, like a real save would
        when(irisAssessmentRepository.save(any(IrisAssessment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(taskScheduler.schedule(any(Runnable.class), any(Instant.class))).thenReturn(mock(ScheduledFuture.class));
    }

    private User user(long id, String login) {
        var user = new User();
        user.setId(id);
        user.setLogin(login);
        return user;
    }

    private ProgrammingExercise exercise(long id) {
        var exercise = new ProgrammingExercise();
        exercise.setId(id);
        return exercise;
    }

    private ProgrammingExerciseStudentParticipation participationWithAssessment(User user, ProgrammingExercise exercise, IrisAssessment assessment, boolean inClass) {
        var participation = new ProgrammingExerciseStudentParticipation();
        participation.setId(1L);
        participation.setProgrammingExercise(exercise);
        participation.setParticipant(user);
        if (inClass) {
            participation.setIrisAssessmentInClass(assessment);
        }
        else {
            participation.setIrisAssessment(assessment);
        }
        return participation;
    }

    // ---------------------------------------------------------------------
    // saveAndHandleVerdict / addReasoning
    // ---------------------------------------------------------------------

    @Test
    void saveAndHandleVerdictCreatesAssessmentWhenNoneExistsAndStoresVerdictAndReasoning() {
        var user = user(1L, "student1");
        var exercise = exercise(2L);
        var participation = participationWithAssessment(user, exercise, null, false);
        when(programmingExerciseStudentParticipationRepository.findWithIrisAssessmentByExerciseIdAndStudentLoginAndTestRun(exercise.getId(), user.getLogin(), false, false))
                .thenReturn(Optional.of(participation));

        irisAssessmentReviewService.saveAndHandleVerdict(user, exercise, new IrisVerdictDTO(IrisVerdict.SUSPICIOUS, "looked copied"), false);

        assertThat(participation.getIrisAssessment()).isNotNull();
        assertThat(participation.getIrisAssessment().getVerdict()).isEqualTo(IrisVerdict.SUSPICIOUS);
        assertThat(participation.getIrisAssessment().getReasoning()).containsExactly("looked copied");
        assertThat(participation.getIrisAssessment().getVerdictReview()).isNull();
    }

    @Test
    void saveAndHandleVerdictResetsPreviousReviewStatusOnNewVerdict() {
        var user = user(1L, "student1");
        var exercise = exercise(2L);
        var assessment = new IrisAssessment(user, exercise);
        assessment.setId(10L);
        assessment.setVerdict(IrisVerdict.UNSUSPICIOUS);
        assessment.setVerdictReview(IrisVerdictReview.ACCEPTED);
        var participation = participationWithAssessment(user, exercise, assessment, false);
        when(programmingExerciseStudentParticipationRepository.findWithIrisAssessmentByExerciseIdAndStudentLoginAndTestRun(exercise.getId(), user.getLogin(), false, false))
                .thenReturn(Optional.of(participation));
        when(irisAssessmentRepository.findWithReasoningById(assessment.getId())).thenReturn(Optional.of(assessment));

        irisAssessmentReviewService.saveAndHandleVerdict(user, exercise, new IrisVerdictDTO(IrisVerdict.SUSPICIOUS, "new reasoning"), false);

        assertThat(assessment.getVerdict()).isEqualTo(IrisVerdict.SUSPICIOUS);
        assertThat(assessment.getVerdictReview()).isNull();
        assertThat(assessment.getReasoning()).containsExactly("new reasoning");
    }

    @Test
    void addReasoningAppendsToExistingReasoningList() {
        var user = user(1L, "student1");
        var exercise = exercise(2L);
        var assessment = new IrisAssessment(user, exercise);
        assessment.setId(10L);
        assessment.setReasoning(new ArrayList<>(List.of("first answer")));
        var participation = participationWithAssessment(user, exercise, assessment, true);
        when(programmingExerciseStudentParticipationRepository.findWithIrisAssessmentByExerciseIdAndStudentLoginAndTestRun(exercise.getId(), user.getLogin(), true, false))
                .thenReturn(Optional.of(participation));
        when(irisAssessmentRepository.findWithReasoningById(assessment.getId())).thenReturn(Optional.of(assessment));

        irisAssessmentReviewService.addReasoning(user, exercise, "second answer", true);

        assertThat(assessment.getReasoning()).containsExactly("first answer", "second answer");
    }

    // ---------------------------------------------------------------------
    // assessmentAttentionNeededInCourse
    // ---------------------------------------------------------------------

    @Test
    void assessmentAttentionNeededInCourseDelegatesToRepositoryForSuspiciousUnreviewedVerdicts() {
        when(irisAssessmentRepository.existsByCourseIdAndVerdictAndVerdictReviewIsNull(5L, IrisVerdict.SUSPICIOUS)).thenReturn(true);

        assertThat(irisAssessmentReviewService.assessmentAttentionNeededInCourse(5L)).isTrue();
    }

    @Test
    void assessmentAttentionNeededInCourseReturnsFalseWhenNoSuspiciousUnreviewedAssessmentExists() {
        when(irisAssessmentRepository.existsByCourseIdAndVerdictAndVerdictReviewIsNull(5L, IrisVerdict.SUSPICIOUS)).thenReturn(false);

        assertThat(irisAssessmentReviewService.assessmentAttentionNeededInCourse(5L)).isFalse();
    }

    // ---------------------------------------------------------------------
    // findAssessmentReviewParticipationsForCourse
    // ---------------------------------------------------------------------

    @Test
    void findAssessmentReviewParticipationsForCourseReturnsEmptyPageWhenNoIdsMatch() {
        var search = new IrisAssessmentReviewSearchDTO(0, 20, SortingOrder.ASCENDING, "name", null, null);
        when(programmingExerciseStudentParticipationRepository.findIrisAssessmentReviewParticipationIds(eq(5L), any(), eq(false), anyBoolean(), anyBoolean(), anyBoolean(),
                anyBoolean(), anyBoolean(), anyBoolean(), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        var result = irisAssessmentReviewService.findAssessmentReviewParticipationsForCourse(5L, search, false);

        assertThat(result.page().getContent()).isEmpty();
        assertThat(result.participationsPerFilter()).containsKeys("All", "Accepted", "Rejected", "Unsuspicious", "Suspicious", "MissingAssessment");
        verify(programmingExerciseStudentParticipationRepository, never()).findAllIrisAssessmentParticipationProjectionsByIdIn(any());
    }

    @Test
    void findAssessmentReviewParticipationsForCourseMapsRegularProjectionsPreservingIdOrder() {
        var search = new IrisAssessmentReviewSearchDTO(0, 20, SortingOrder.ASCENDING, "name", null, null);
        var pageable = PageRequest.of(0, 20);
        when(programmingExerciseStudentParticipationRepository.findIrisAssessmentReviewParticipationIds(eq(5L), any(), eq(false), anyBoolean(), anyBoolean(), anyBoolean(),
                anyBoolean(), anyBoolean(), anyBoolean(), any())).thenReturn(new PageImpl<>(List.of(2L, 1L), pageable, 2));

        var projection1 = new IrisAssessmentProgrammingStudentParticipationProjectionDTO(1L, 9L, "uri1", "plan1", "student1", "First", "Student", null, null, null);
        var projection2 = new IrisAssessmentProgrammingStudentParticipationProjectionDTO(2L, 9L, "uri2", "plan2", "student2", "Second", "Student", 20L, IrisVerdict.SUSPICIOUS,
                null);
        when(programmingExerciseStudentParticipationRepository.findAllIrisAssessmentParticipationProjectionsByIdIn(Set.of(1L, 2L))).thenReturn(Set.of(projection1, projection2));
        when(studentParticipationRepository.countSubmissionsPerParticipationByIdsAsMap(List.of(2L, 1L))).thenReturn(Map.of(1L, 3, 2L, 1));

        var result = irisAssessmentReviewService.findAssessmentReviewParticipationsForCourse(5L, search, false);

        assertThat(result.page().getContent()).extracting(IrisAssessmentProgrammingStudentParticipationDTO::id).containsExactly(2L, 1L);
        verify(programmingExerciseStudentParticipationRepository, never()).findAllIrisAssessmentInClassParticipationProjectionsByIdIn(any());
    }

    @Test
    void findAssessmentReviewParticipationsForCourseUsesInClassProjectionsWhenInClass() {
        var search = new IrisAssessmentReviewSearchDTO(0, 20, SortingOrder.ASCENDING, "name", null, null);
        var pageable = PageRequest.of(0, 20);
        when(programmingExerciseStudentParticipationRepository.findIrisAssessmentReviewParticipationIds(eq(5L), any(), eq(true), anyBoolean(), anyBoolean(), anyBoolean(),
                anyBoolean(), anyBoolean(), anyBoolean(), any())).thenReturn(new PageImpl<>(List.of(1L), pageable, 1));
        var projection = new IrisAssessmentProgrammingStudentParticipationProjectionDTO(1L, 9L, "uri1", "plan1", "student1", "First", "Student", null, null, null);
        when(programmingExerciseStudentParticipationRepository.findAllIrisAssessmentInClassParticipationProjectionsByIdIn(Set.of(1L))).thenReturn(Set.of(projection));
        when(studentParticipationRepository.countSubmissionsPerParticipationByIdsAsMap(List.of(1L))).thenReturn(Map.of(1L, 2));

        var result = irisAssessmentReviewService.findAssessmentReviewParticipationsForCourse(5L, search, true);

        assertThat(result.page().getContent()).hasSize(1);
        verify(programmingExerciseStudentParticipationRepository, never()).findAllIrisAssessmentParticipationProjectionsByIdIn(any());
    }

    @Test
    void findAssessmentReviewParticipationsForCourseParsesSelectedFiltersFromFilterProps() {
        var search = new IrisAssessmentReviewSearchDTO(0, 20, SortingOrder.ASCENDING, "name", "  Ann  ", "Accepted,Suspicious");
        when(programmingExerciseStudentParticipationRepository.findIrisAssessmentReviewParticipationIds(anyLong(), anyString(), anyBoolean(), anyBoolean(), anyBoolean(),
                anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        irisAssessmentReviewService.findAssessmentReviewParticipationsForCourse(5L, search, false);

        // main query call: hasSelectedFilter=true, accepted=true, rejected=false, unsuspicious=false, suspicious=true, missing=false
        verify(programmingExerciseStudentParticipationRepository).findIrisAssessmentReviewParticipationIds(5L, "%ann%", false, true, true, false, false, true, false,
                PageRequest.of(0, 20));
    }

    @Test
    void findAssessmentReviewParticipationsForCourseConvertsBlankSearchTermToNullPatternAndSelectsNoFilterWhenFilterPropsIsBlank() {
        var search = new IrisAssessmentReviewSearchDTO(0, 20, SortingOrder.ASCENDING, "name", "   ", "  ");
        when(programmingExerciseStudentParticipationRepository.findIrisAssessmentReviewParticipationIds(anyLong(), any(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(),
                anyBoolean(), anyBoolean(), anyBoolean(), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        irisAssessmentReviewService.findAssessmentReviewParticipationsForCourse(5L, search, false);

        verify(programmingExerciseStudentParticipationRepository).findIrisAssessmentReviewParticipationIds(5L, null, false, false, false, false, false, false, false,
                PageRequest.of(0, 20));
    }

    @Test
    void findAssessmentReviewParticipationsForCourseEscapesBackslashPercentAndUnderscoreInSearchTerm() {
        var search = new IrisAssessmentReviewSearchDTO(0, 20, SortingOrder.ASCENDING, "name", "50%_Match\\Case", null);
        when(programmingExerciseStudentParticipationRepository.findIrisAssessmentReviewParticipationIds(anyLong(), any(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(),
                anyBoolean(), anyBoolean(), anyBoolean(), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        irisAssessmentReviewService.findAssessmentReviewParticipationsForCourse(5L, search, false);

        // "50%_Match\Case" -> lowercased "50%_match\case" -> backslash, percent, and underscore escaped
        verify(programmingExerciseStudentParticipationRepository).findIrisAssessmentReviewParticipationIds(5L, "%50\\%\\_match\\\\case%", false, false, false, false, false, false,
                false, PageRequest.of(0, 20));
    }

    // ---------------------------------------------------------------------
    // resetVerdictAndReasoning
    // ---------------------------------------------------------------------

    @Test
    void resetVerdictAndReasoningByUserAndExerciseClearsVerdictAndReasoning() {
        var user = user(1L, "student1");
        var exercise = exercise(2L);
        var assessment = new IrisAssessment(user, exercise);
        assessment.setId(10L);
        assessment.setVerdict(IrisVerdict.SUSPICIOUS);
        assessment.setReasoning(new ArrayList<>(List.of("a", "b")));
        var participation = participationWithAssessment(user, exercise, assessment, false);
        when(programmingExerciseStudentParticipationRepository.findWithIrisAssessmentByExerciseIdAndStudentLoginAndTestRun(exercise.getId(), user.getLogin(), false, false))
                .thenReturn(Optional.of(participation));
        when(irisAssessmentRepository.findWithReasoningById(assessment.getId())).thenReturn(Optional.of(assessment));

        irisAssessmentReviewService.resetVerdictAndReasoning(user, exercise, false);

        assertThat(assessment.getVerdict()).isNull();
        assertThat(assessment.getReasoning()).isEmpty();
    }

    @Test
    void resetVerdictAndReasoningByAssessmentRefetchesWithReasoningWhenIdPresent() {
        var user = user(1L, "student1");
        var exercise = exercise(2L);
        var staleAssessment = new IrisAssessment(user, exercise);
        staleAssessment.setId(10L);
        var freshAssessment = new IrisAssessment(user, exercise);
        freshAssessment.setId(10L);
        freshAssessment.setVerdict(IrisVerdict.SUSPICIOUS);
        freshAssessment.setReasoning(new ArrayList<>(List.of("a")));
        when(irisAssessmentRepository.findWithReasoningById(10L)).thenReturn(Optional.of(freshAssessment));

        irisAssessmentReviewService.resetVerdictAndReasoning(staleAssessment);

        assertThat(freshAssessment.getVerdict()).isNull();
        assertThat(freshAssessment.getReasoning()).isEmpty();
        verify(irisAssessmentRepository).save(freshAssessment);
    }

    @Test
    void resetVerdictAndReasoningByAssessmentDoesNotRefetchWhenIdIsNull() {
        var user = user(1L, "student1");
        var exercise = exercise(2L);
        var assessment = new IrisAssessment(user, exercise);

        irisAssessmentReviewService.resetVerdictAndReasoning(assessment);

        verify(irisAssessmentRepository, never()).findWithReasoningById(anyLong());
        verify(irisAssessmentRepository).save(assessment);
    }

    // ---------------------------------------------------------------------
    // acceptAnswers / rejectAnswers
    // ---------------------------------------------------------------------

    @Test
    void acceptAnswersIsNoOpWhenAlreadyAccepted() {
        var assessment = new IrisAssessment(user(1L, "student1"), exercise(2L));
        assessment.setVerdict(IrisVerdict.SUSPICIOUS);
        assessment.setVerdictReview(IrisVerdictReview.ACCEPTED);

        irisAssessmentReviewService.acceptAnswers(assessment);

        verify(irisAssessmentRepository, never()).save(any());
    }

    @Test
    void acceptAnswersThrowsWhenVerdictIsNull() {
        var assessment = new IrisAssessment(user(1L, "student1"), exercise(2L));

        assertThatThrownBy(() -> irisAssessmentReviewService.acceptAnswers(assessment)).isInstanceOf(ConflictException.class);
        verify(irisAssessmentRepository, never()).save(any());
    }

    @Test
    void acceptAnswersSetsAcceptedReviewStatusAndSaves() {
        var assessment = new IrisAssessment(user(1L, "student1"), exercise(2L));
        assessment.setVerdict(IrisVerdict.UNSUSPICIOUS);

        irisAssessmentReviewService.acceptAnswers(assessment);

        assertThat(assessment.getVerdictReview()).isEqualTo(IrisVerdictReview.ACCEPTED);
        verify(irisAssessmentRepository).save(assessment);
    }

    @Test
    void rejectAnswersIsNoOpWhenAlreadyRejected() {
        var assessment = new IrisAssessment(user(1L, "student1"), exercise(2L));
        assessment.setVerdict(IrisVerdict.SUSPICIOUS);
        assessment.setVerdictReview(IrisVerdictReview.REJECTED);

        irisAssessmentReviewService.rejectAnswers(assessment);

        verify(irisAssessmentRepository, never()).save(any());
    }

    @Test
    void rejectAnswersThrowsWhenVerdictIsNull() {
        var assessment = new IrisAssessment(user(1L, "student1"), exercise(2L));

        assertThatThrownBy(() -> irisAssessmentReviewService.rejectAnswers(assessment)).isInstanceOf(ConflictException.class);
    }

    @Test
    void rejectAnswersSetsRejectedReviewStatusAndSaves() {
        var assessment = new IrisAssessment(user(1L, "student1"), exercise(2L));
        assessment.setVerdict(IrisVerdict.SUSPICIOUS);

        irisAssessmentReviewService.rejectAnswers(assessment);

        assertThat(assessment.getVerdictReview()).isEqualTo(IrisVerdictReview.REJECTED);
        verify(irisAssessmentRepository).save(assessment);
    }

    // ---------------------------------------------------------------------
    // createNewAssessment
    // ---------------------------------------------------------------------

    @Test
    void createNewAssessmentThrowsForPracticeParticipation() {
        var user = user(1L, "student1");
        var exercise = exercise(2L);
        var participation = participationWithAssessment(user, exercise, null, false);
        participation.setTestRun(true);

        assertThatThrownBy(() -> irisAssessmentReviewService.createNewAssessment(participation)).isInstanceOf(IllegalStateException.class);
        verify(irisAssessmentRepository, never()).save(any());
    }

    @Test
    void createNewAssessmentSetsRegularAssessmentOnParticipationByDefault() {
        var user = user(1L, "student1");
        var exercise = exercise(2L);
        var participation = participationWithAssessment(user, exercise, null, false);

        var assessment = irisAssessmentReviewService.createNewAssessment(participation);

        assertThat(participation.getIrisAssessment()).isSameAs(assessment);
        assertThat(participation.getIrisAssessmentInClass()).isNull();
        verify(programmingExerciseStudentParticipationRepository).save(participation);
    }

    @Test
    void createNewAssessmentSetsInClassAssessmentOnParticipationWhenRequested() {
        var user = user(1L, "student1");
        var exercise = exercise(2L);
        var participation = participationWithAssessment(user, exercise, null, false);

        var assessment = irisAssessmentReviewService.createNewAssessment(participation, true);

        assertThat(participation.getIrisAssessmentInClass()).isSameAs(assessment);
        assertThat(participation.getIrisAssessment()).isNull();
    }

    // ---------------------------------------------------------------------
    // getQAExchangeDTOList
    // ---------------------------------------------------------------------

    @Test
    void getQAExchangeDTOListThrowsForNonProgrammingExercise() {
        var user = user(1L, "student1");
        var exercise = new TextExercise();
        exercise.setId(2L);
        var assessment = new IrisAssessment(user, exercise);

        assertThatThrownBy(() -> irisAssessmentReviewService.getQAExchangeDTOList(assessment, exercise, user, false)).isInstanceOf(ConflictException.class);
    }

    @Test
    void getQAExchangeDTOListThrowsWhenAssessmentIsNull() {
        var user = user(1L, "student1");
        var exercise = exercise(2L);
        when(irisChatSessionRepository.findLatestFinishedAskUserModeSessionByExerciseIdAndUserIdAndInClassQuizElseThrow(exercise.getId(), user.getId(), false))
                .thenReturn(new IrisChatSession());

        assertThatThrownBy(() -> irisAssessmentReviewService.getQAExchangeDTOList(null, exercise, user, false)).isInstanceOf(ConflictException.class);
    }

    @Test
    void getQAExchangeDTOListThrowsWhenReasoningIsMissing() {
        var user = user(1L, "student1");
        var exercise = exercise(2L);
        var assessment = new IrisAssessment(user, exercise);
        when(irisChatSessionRepository.findLatestFinishedAskUserModeSessionByExerciseIdAndUserIdAndInClassQuizElseThrow(exercise.getId(), user.getId(), false))
                .thenReturn(new IrisChatSession());

        assertThatThrownBy(() -> irisAssessmentReviewService.getQAExchangeDTOList(assessment, exercise, user, false)).isInstanceOf(ConflictException.class)
                .hasMessageContaining("reasoning");
    }

    @Test
    void getQAExchangeDTOListPairsQuestionsAndAnswersSkippingExplanationAndFinishedMessages() {
        var user = user(1L, "student1");
        var exercise = exercise(2L);
        var assessment = new IrisAssessment(user, exercise);
        assessment.setReasoning(List.of("unsuspicious after Q1", "suspicious after Q2"));

        var session = new IrisChatSession();
        // LLM explanation message (skipped: first LLM message)
        session.getMessages().add(askUserMessage(IrisMessageSender.LLM, "Let's start the quiz."));
        // Q1 / A1
        session.getMessages().add(askUserMessage(IrisMessageSender.LLM, "Question 1?"));
        session.getMessages().add(askUserMessage(IrisMessageSender.USER, "Answer 1"));
        // Q2 / A2
        session.getMessages().add(askUserMessage(IrisMessageSender.LLM, "Question 2?"));
        session.getMessages().add(askUserMessage(IrisMessageSender.USER, "Answer 2"));
        // LLM quiz_finished message (skipped: last LLM message)
        session.getMessages().add(askUserMessage(IrisMessageSender.LLM, "Quiz finished."));
        // a message outside ask-user mode must be ignored entirely
        var outOfModeMessage = new IrisMessage();
        outOfModeMessage.setSender(IrisMessageSender.USER);
        outOfModeMessage.addContent(new IrisTextMessageContent("regular chat message"));
        outOfModeMessage.setInAskUserMode(false);
        session.getMessages().add(outOfModeMessage);

        when(irisChatSessionRepository.findLatestFinishedAskUserModeSessionByExerciseIdAndUserIdAndInClassQuizElseThrow(exercise.getId(), user.getId(), false)).thenReturn(session);

        var result = irisAssessmentReviewService.getQAExchangeDTOList(assessment, exercise, user, false);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).question()).isEqualTo("Question 1?");
        assertThat(result.get(0).answer()).isEqualTo("Answer 1");
        assertThat(result.get(0).reasoning()).isEqualTo("unsuspicious after Q1");
        assertThat(result.get(1).question()).isEqualTo("Question 2?");
        assertThat(result.get(1).answer()).isEqualTo("Answer 2");
        assertThat(result.get(1).reasoning()).isEqualTo("suspicious after Q2");
    }

    private IrisMessage askUserMessage(IrisMessageSender sender, String text) {
        var message = new IrisMessage();
        message.setSender(sender);
        message.addContent(new IrisTextMessageContent(text));
        message.setInAskUserMode(true);
        return message;
    }

    // ---------------------------------------------------------------------
    // in-class quiz timer
    // ---------------------------------------------------------------------

    @Test
    void validateInClassQuizIsAvailableOrElseThrowThrowsWhenNoTimerIsActive() {
        var exercise = exercise(2L);

        assertThatThrownBy(() -> irisAssessmentReviewService.validateInClassQuizIsAvailableOrElseThrow(exercise)).isInstanceOf(ConflictException.class);
    }

    @Test
    void validateInClassQuizIsAvailableOrElseThrowDoesNotThrowWhenTimerIsActive() {
        var exercise = exercise(2L);
        exercise.setIrisInClassQuizTimer(ZonedDateTime.now().plusMinutes(5));

        irisAssessmentReviewService.validateInClassQuizIsAvailableOrElseThrow(exercise);
        // no exception
    }

    @Test
    void getAvailableInClassQuizReturnsNullWhenNoTimerIsSet() {
        var exercise = exercise(2L);

        assertThat(irisAssessmentReviewService.getAvailableInClassQuiz(exercise)).isNull();
    }

    @Test
    void getAvailableInClassQuizClearsExpiredTimerAndReturnsNull() {
        var exercise = exercise(2L);
        exercise.setIrisInClassQuizTimer(ZonedDateTime.now().minusSeconds(5));

        var result = irisAssessmentReviewService.getAvailableInClassQuiz(exercise);

        assertThat(result).isNull();
        assertThat(exercise.getIrisInClassQuizTimer()).isNull();
        verify(programmingExerciseRepository).save(exercise);
    }

    @Test
    void getAvailableInClassQuizReturnsRemainingSecondsWhenTimerIsActive() {
        var exercise = exercise(2L);
        exercise.setIrisInClassQuizTimer(ZonedDateTime.now().plusMinutes(2));

        var result = irisAssessmentReviewService.getAvailableInClassQuiz(exercise);

        assertThat(result).isNotNull();
        assertThat(result.timeLimit()).isPositive().isLessThanOrEqualTo(120);
        verify(programmingExerciseRepository, never()).save(any());
    }

    // ---------------------------------------------------------------------
    // makeInClassQuizAvailable
    // ---------------------------------------------------------------------

    @Test
    void makeInClassQuizAvailableThrowsForExamExercise() {
        var exercise = exercise(2L);
        exercise.setExerciseGroup(new ExerciseGroup());

        assertThatThrownBy(() -> irisAssessmentReviewService.makeInClassQuizAvailable(exercise)).isInstanceOf(ConflictException.class);
        verify(irisSettingsService, never()).ensureAskUserModeEnabledForExerciseOrElseThrow(any());
    }

    @Test
    void makeInClassQuizAvailablePropagatesExceptionWhenAskUserModeDisabled() {
        var exercise = exercise(2L);
        doAnswer(invocation -> {
            throw new AccessForbiddenAlertException("disabled", "Iris", "iris.ask_user_mode_disabled");
        }).when(irisSettingsService).ensureAskUserModeEnabledForExerciseOrElseThrow(exercise);

        assertThatThrownBy(() -> irisAssessmentReviewService.makeInClassQuizAvailable(exercise)).isInstanceOf(AccessForbiddenAlertException.class);
        verify(programmingExerciseStudentParticipationRepository, never()).findIrisAssessmentInClassIdsByExerciseId(anyLong());
    }

    @Test
    void makeInClassQuizAvailableDeletesExistingInClassAssessmentsAndStartsTimer() {
        var exercise = exercise(2L);
        when(programmingExerciseStudentParticipationRepository.findIrisAssessmentInClassIdsByExerciseId(exercise.getId())).thenReturn(Set.of(11L, 12L));
        when(irisSettingsService.getSettingsForExercise(exercise)).thenReturn(IrisCourseSettings.defaultSettings());

        var result = irisAssessmentReviewService.makeInClassQuizAvailable(exercise);

        verify(programmingExerciseStudentParticipationRepository).unsetIrisAssessmentInClassByExerciseId(exercise.getId());
        verify(irisAssessmentRepository).deleteAllByIdInBulk(Set.of(11L, 12L));
        verify(irisAssessmentQuizWebsocketService).sendInClassQuizStarted(exercise.getId());
        verify(programmingExerciseRepository).save(exercise);
        verify(taskScheduler).schedule(any(Runnable.class), any(Instant.class));
        assertThat(exercise.getIrisInClassQuizTimer()).isNotNull();
        assertThat(result.timeLimit()).isEqualTo(IrisCourseSettings.defaultSettings().askUserModeSettings().timeLimitInClass() * 60);
    }

    @Test
    void makeInClassQuizAvailableSkipsDeletionWhenNoInClassAssessmentsExist() {
        var exercise = exercise(2L);
        when(programmingExerciseStudentParticipationRepository.findIrisAssessmentInClassIdsByExerciseId(exercise.getId())).thenReturn(Set.of());
        when(irisSettingsService.getSettingsForExercise(exercise)).thenReturn(IrisCourseSettings.defaultSettings());

        irisAssessmentReviewService.makeInClassQuizAvailable(exercise);

        verify(programmingExerciseStudentParticipationRepository, never()).unsetIrisAssessmentInClassByExerciseId(anyLong());
        verify(irisAssessmentRepository, never()).deleteAllByIdInBulk(any());
    }
}
