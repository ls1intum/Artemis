package de.tum.cit.aet.artemis.iris.service.session;

import static de.tum.cit.aet.artemis.iris.util.IrisMessageContentFactory.solutionOffer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.iris.domain.message.IrisJsonMessageContent;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.message.IrisSolutionChoice;
import de.tum.cit.aet.artemis.iris.domain.message.IrisSolutionStep;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisTutorSuggestionSession;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettings;
import de.tum.cit.aet.artemis.iris.dto.IrisCombinedViewContextDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisMessageContextDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisSlidesContextDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisSolutionChoiceDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisVideoContextDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisDTOService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisPipelineService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisChatPipelineExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisProgrammingExerciseDTO;
import de.tum.cit.aet.artemis.lecture.api.LectureRepositoryApi;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.TextUnit;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.text.api.TextRepositoryApi;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

class IrisChatPipelineContextTest {

    private IrisSessionRepository sessions;

    private CourseTestRepository courses;

    private LectureRepositoryApi lectures;

    private PyrisPipelineService pipelines;

    private ExerciseTestRepository exercises;

    private ProgrammingExerciseTestRepository programmingExercises;

    private PyrisDTOService dtoService;

    private AuthorizationCheckService authCheck;

    private IrisChatPipelineExecutionService service;

    private IrisChatSession session;

    private User user;

    private Course course;

    private TextRepositoryApi texts;

    @BeforeEach
    void setUp() {
        sessions = mock(IrisSessionRepository.class);
        courses = mock(CourseTestRepository.class);
        lectures = mock(LectureRepositoryApi.class);
        pipelines = mock(PyrisPipelineService.class);
        exercises = mock(ExerciseTestRepository.class);
        programmingExercises = mock(ProgrammingExerciseTestRepository.class);
        dtoService = mock(PyrisDTOService.class);
        authCheck = mock(AuthorizationCheckService.class);
        texts = mock(TextRepositoryApi.class);
        service = new IrisChatPipelineExecutionService(sessions, courses, exercises, programmingExercises, mock(ProgrammingExerciseStudentParticipationRepository.class),
                mock(ProgrammingSubmissionRepository.class), mock(StudentParticipationRepository.class), Optional.of(texts), Optional.of(lectures), null, dtoService, pipelines,
                authCheck);
        course = new Course();
        course.setId(1L);
        user = new User();
        user.setId(2L);
        var lecture = new Lecture();
        lecture.setId(3L);
        lecture.setCourse(course);
        session = new IrisChatSession(lecture, user);
        session.setId(4L);
    }

    @Test
    void lectureContextRejectsUnknownAndMismatchingUnitsAndPreservesValidNestedContext() {
        when(sessions.findByIdWithMessagesAndContents(4L)).thenReturn(session);
        when(courses.findByIdElseThrow(1L)).thenReturn(course);
        when(pipelines.loadCourseWithParticipationOfStudent(1L, 2L)).thenReturn(course);
        var lecture = new Lecture();
        lecture.setId(3L);
        var text = new TextUnit();
        text.setId(5L);
        var video = new AttachmentVideoUnit();
        video.setId(6L);
        var attachment = new Attachment();
        attachment.setVersion(7);
        video.setAttachment(attachment);
        lecture.setLectureUnits(List.of(text, video));
        when(lectures.findByIdWithLectureUnitsElseThrow(3L)).thenReturn(lecture);
        var slides = new IrisSlidesContextDTO(5L, 1);
        var videoContext = new IrisVideoContextDTO(6L, 1.0);
        var onlySlides = new IrisCombinedViewContextDTO(slides, null);
        var onlyVideo = new IrisCombinedViewContextDTO(null, videoContext);
        var both = new IrisCombinedViewContextDTO(new IrisSlidesContextDTO(6L, 2), videoContext);
        List<IrisMessageContextDTO> context = List.of(slides, videoContext, onlySlides, onlyVideo, both, new IrisVideoContextDTO(99L, 0.0), new IrisSlidesContextDTO(99L, 1),
                new IrisCombinedViewContextDTO(null, null), new IrisCombinedViewContextDTO(slides, videoContext),
                new IrisCombinedViewContextDTO(new IrisSlidesContextDTO(99L, 1), null), new IrisCombinedViewContextDTO(null, new IrisVideoContextDTO(99L, 0.0)));
        service.execute(session, Optional.empty(), Optional.of(IrisCourseSettings.defaultSettings()), Optional.empty(), Map.of(), context, "tab");
        var builder = ArgumentCaptor.forClass(PyrisPipelineService.ChatPipelineDTOBuilder.class);
        verify(pipelines).executeChatPipeline(any(), any(), any(), any(), any(), builder.capture());
        var dto = builder.getValue().apply(new PyrisPipelineExecutionDTO(null), user, null);
        assertThat(dto.context()).containsExactly(slides, videoContext, onlySlides, onlyVideo, both);
        assertThat(dto.lectureUnitId()).isEqualTo(5L);
        assertThat(dto.lecture().units()).extracting(unit -> unit.attachmentVersion()).containsExactly(null, 7);
    }

    @Test
    void unreleasedLectureUnitsAreOnlyForwardedForStaff() {
        when(sessions.findByIdWithMessagesAndContents(4L)).thenReturn(session);
        when(courses.findByIdElseThrow(1L)).thenReturn(course);
        when(pipelines.loadCourseWithParticipationOfStudent(1L, 2L)).thenReturn(course);
        var lecture = new Lecture();
        lecture.setId(3L);
        var released = new TextUnit();
        released.setId(5L);
        released.setReleaseDate(ZonedDateTime.now().minusDays(1));
        var unreleased = new TextUnit();
        unreleased.setId(6L);
        unreleased.setReleaseDate(ZonedDateTime.now().plusDays(1));
        lecture.setLectureUnits(List.of(released, unreleased));
        when(lectures.findByIdWithLectureUnitsElseThrow(3L)).thenReturn(lecture);

        assertThat(runChatPipeline().lecture().units()).extracting(unit -> unit.lectureUnitId()).containsExactly(5L);

        when(authCheck.isAtLeastTeachingAssistantInCourse(course, user)).thenReturn(true);
        assertThat(runChatPipeline().lecture().units()).extracting(unit -> unit.lectureUnitId()).containsExactly(5L, 6L);
    }

    @Test
    void unreleasedExercisesAreOnlyForwardedForStaff() {
        when(sessions.findByIdWithMessagesAndContents(4L)).thenReturn(session);
        when(courses.findByIdElseThrow(1L)).thenReturn(course);
        var released = new TextExercise();
        released.setId(10L);
        released.setReleaseDate(ZonedDateTime.now().minusDays(1));
        var unreleased = new TextExercise();
        unreleased.setId(11L);
        unreleased.setReleaseDate(ZonedDateTime.now().plusDays(1));
        course.setExercises(new HashSet<>(Set.of(released, unreleased)));
        when(pipelines.loadCourseWithParticipationOfStudent(1L, 2L)).thenReturn(course);
        var lecture = new Lecture();
        lecture.setId(3L);
        lecture.setLectureUnits(List.of());
        when(lectures.findByIdWithLectureUnitsElseThrow(3L)).thenReturn(lecture);

        assertThat(runChatPipeline().course().exercises()).extracting(exercise -> exercise.id()).containsExactly(10L);
    }

    @Test
    void programmingExerciseChatDoesNotForwardRepositoryContents() {
        var exercise = new ProgrammingExercise();
        exercise.setId(20L);
        exercise.setCourse(course);
        var programmingSession = new IrisChatSession(exercise, user, IrisChatMode.PROGRAMMING_EXERCISE_CHAT);
        programmingSession.setId(4L);
        when(sessions.findByIdWithMessagesAndContents(4L)).thenReturn(programmingSession);
        when(courses.findByIdElseThrow(1L)).thenReturn(course);
        when(pipelines.loadCourseWithParticipationOfStudent(1L, 2L)).thenReturn(course);
        when(exercises.findByIdElseThrow(20L)).thenReturn(exercise);
        when(programmingExercises.findByIdWithTemplateAndSolutionParticipationElseThrow(20L)).thenReturn(exercise);
        when(dtoService.toPyrisProgrammingExerciseMetadataDTO(exercise))
                .thenReturn(new PyrisProgrammingExerciseDTO(20L, "title", null, Map.of(), Map.of(), Map.of(), "", null, null));

        service.execute(programmingSession, Optional.empty(), Optional.of(IrisCourseSettings.defaultSettings()), Optional.empty(), Map.of(), List.of(), "tab");
        var builder = ArgumentCaptor.forClass(PyrisPipelineService.ChatPipelineDTOBuilder.class);
        verify(pipelines).executeChatPipeline(any(), any(), any(), any(), any(), builder.capture());
        builder.getValue().apply(new PyrisPipelineExecutionDTO(null), user, null);

        verify(dtoService).toPyrisProgrammingExerciseMetadataDTO(exercise);
        verify(dtoService, never()).toPyrisProgrammingExerciseDTO(any());
    }

    private PyrisChatPipelineExecutionDTO runChatPipeline() {
        return runChatPipeline(Optional.empty());
    }

    private PyrisChatPipelineExecutionDTO runChatPipeline(Optional<String> event) {
        service.execute(session, event, Optional.of(IrisCourseSettings.defaultSettings()), Optional.empty(), Map.of(), List.of(), "tab");
        var builder = ArgumentCaptor.forClass(PyrisPipelineService.ChatPipelineDTOBuilder.class);
        verify(pipelines, atLeastOnce()).executeChatPipeline(any(), any(), any(), any(), any(), builder.capture());
        return builder.getValue().apply(new PyrisPipelineExecutionDTO(null), user, null);
    }

    @Test
    void solutionChoiceSendsOnlyTheProgrammingSolution() {
        var exercise = prepareProgrammingReferenceContext();
        appendChoice(IrisSolutionChoice.SHOW_SOLUTION);
        var dto = runChatPipeline();
        assertThat(dto.solutionStep()).isEqualTo(IrisSolutionStep.SHOW_SOLUTION);
        var result = dto.programmingExercise();
        assertThat(result.solutionRepository()).containsEntry("Main.java", "INSTRUCTOR SOLUTION");
        assertThat(result.templateRepository()).isEmpty();
        assertThat(result.testRepository()).isEmpty();
        verify(dtoService).toPyrisProgrammingExerciseWithSolutionDTO(exercise);
        verify(dtoService, never()).toPyrisProgrammingExerciseDTO(any());
    }

    /** Every choice but the explicit one for the solution is its own step and keeps the reference back; a declined summary is no step. */
    @ParameterizedTest
    @CsvSource({ "TRY_MYSELF, TRY_MYSELF", "SUMMARIZE, SUMMARIZE", "SKIP_SUMMARY, " })
    void otherChoicesWithholdTheProgrammingSolution(IrisSolutionChoice choice, IrisSolutionStep step) {
        prepareProgrammingReferenceContext();
        appendChoice(choice);
        var dto = runChatPipeline();
        assertThat(dto.solutionStep()).isEqualTo(step);
        var result = dto.programmingExercise();
        assertThat(result.solutionRepository()).isEmpty();
        assertThat(result.templateRepository()).isEmpty();
        assertThat(result.testRepository()).isEmpty();
        verify(dtoService, never()).toPyrisProgrammingExerciseWithSolutionDTO(any());
        verify(dtoService, never()).toPyrisProgrammingExerciseDTO(any());
    }

    @ParameterizedTest
    @CsvSource({ "SHOW_SOLUTION, INSTRUCTOR TEXT SOLUTION", "TRY_MYSELF, " })
    void textExampleSolutionIsSentOnlyForTheExplicitSolutionChoice(IrisSolutionChoice choice, String expectedSolution) {
        var exercise = new TextExercise();
        exercise.setId(30L);
        exercise.setCourse(course);
        exercise.setExampleSolution("INSTRUCTOR TEXT SOLUTION");
        session = new IrisChatSession(exercise, user, IrisChatMode.TEXT_EXERCISE_CHAT);
        session.setId(4L);
        when(sessions.findByIdWithMessagesAndContents(4L)).thenReturn(session);
        when(courses.findByIdElseThrow(1L)).thenReturn(course);
        when(pipelines.loadCourseWithParticipationOfStudent(1L, 2L)).thenReturn(course);
        when(exercises.findByIdElseThrow(30L)).thenReturn(exercise);
        when(texts.findByIdElseThrow(30L)).thenReturn(exercise);
        appendChoice(choice);
        assertThat(runChatPipeline().textExercise().exampleSolution()).isEqualTo(Optional.ofNullable(expectedSolution));
    }

    @Test
    void messageAfterTheSummaryRequestIsItsExplanationWithoutTheReference() {
        prepareProgrammingReferenceContext();
        appendChoice(IrisSolutionChoice.SUMMARIZE);
        session.getMessages().addAll(List.of(message(IrisMessageSender.LLM, "In short, the loop compares neighbours. Why does it start at 1?"),
                message(IrisMessageSender.USER, "Because the first element is the start value.")));
        var result = runChatPipeline();
        assertThat(result.solutionStep()).isEqualTo(IrisSolutionStep.EXPLAIN_SOLUTION);
        assertThat(result.programmingExercise().solutionRepository()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({ "OFFER_REPLY, INSTRUCTOR SOLUTION", "SUMMARY_REPLY, " })
    void typedReplyToAnOfferIsItsReplyStepAndOnlyTheSolutionOfferSendsTheReference(IrisSolutionStep step, String sentSolution) {
        prepareProgrammingReferenceContext();
        appendOffer(step == IrisSolutionStep.OFFER_REPLY ? SOLUTION_CHOICES : SUMMARY_CHOICES);
        session.getMessages().add(message(IrisMessageSender.USER, "yes"));
        var result = runChatPipeline();
        assertThat(result.solutionStep()).isEqualTo(step);
        assertThat(result.programmingExercise().solutionRepository().get("Main.java")).isEqualTo(sentSolution);
    }

    @ParameterizedTest
    @CsvSource({ "SUMMARIZE, EXPLAIN_SOLUTION", "SKIP_SUMMARY, " })
    void messageAfterATypedSummaryChoiceIsAnExplanationOnlyIfItAccepted(IrisSolutionChoice choice, IrisSolutionStep step) {
        prepareProgrammingReferenceContext();
        appendOffer(SUMMARY_CHOICES);
        session.getMessages().add(message(IrisMessageSender.USER, "ok"));
        var answer = message(IrisMessageSender.LLM, "Then explain in your own words: why does it start at 1?");
        answer.addContent(IrisJsonMessageContent.of(new IrisSolutionChoiceDTO(10L, choice)));
        session.getMessages().addAll(List.of(answer, message(IrisMessageSender.USER, "The loop skips the first element.")));
        var result = runChatPipeline();
        assertThat(result.solutionStep()).isEqualTo(step);
    }

    @Test
    void eventTriggeredRunNeverContinuesTheStudentsChoice() {
        prepareProgrammingReferenceContext();
        appendChoice(IrisSolutionChoice.SHOW_SOLUTION);
        var result = runChatPipeline(Optional.of("build_failed"));
        assertThat(result.solutionStep()).isNull();
        assertThat(result.programmingExercise().solutionRepository()).isEmpty();
        verify(dtoService, never()).toPyrisProgrammingExerciseWithSolutionDTO(any());
    }

    private ProgrammingExercise prepareProgrammingReferenceContext() {
        var exercise = new ProgrammingExercise();
        exercise.setId(20L);
        exercise.setCourse(course);
        session = new IrisChatSession(exercise, user, IrisChatMode.PROGRAMMING_EXERCISE_CHAT);
        session.setId(4L);
        when(sessions.findByIdWithMessagesAndContents(4L)).thenReturn(session);
        when(courses.findByIdElseThrow(1L)).thenReturn(course);
        when(pipelines.loadCourseWithParticipationOfStudent(1L, 2L)).thenReturn(course);
        when(exercises.findByIdElseThrow(20L)).thenReturn(exercise);
        when(programmingExercises.findByIdWithTemplateAndSolutionParticipationElseThrow(20L)).thenReturn(exercise);
        when(dtoService.toPyrisProgrammingExerciseMetadataDTO(exercise))
                .thenReturn(new PyrisProgrammingExerciseDTO(20L, "title", null, Map.of(), Map.of(), Map.of(), "task", null, null));
        when(dtoService.toPyrisProgrammingExerciseWithSolutionDTO(exercise))
                .thenReturn(new PyrisProgrammingExerciseDTO(20L, "title", null, Map.of(), Map.of("Main.java", "INSTRUCTOR SOLUTION"), Map.of(), "task", null, null));
        return exercise;
    }

    private static final List<IrisSolutionChoice> SOLUTION_CHOICES = List.of(IrisSolutionChoice.TRY_MYSELF, IrisSolutionChoice.SHOW_SOLUTION);

    private static final List<IrisSolutionChoice> SUMMARY_CHOICES = List.of(IrisSolutionChoice.SUMMARIZE, IrisSolutionChoice.SKIP_SUMMARY);

    private void appendChoice(IrisSolutionChoice choice) {
        appendOffer(SOLUTION_CHOICES.contains(choice) ? SOLUTION_CHOICES : SUMMARY_CHOICES);
        var action = message(IrisMessageSender.USER, "Show me the complete solution");
        action.addContent(IrisJsonMessageContent.of(new IrisSolutionChoiceDTO(10L, choice)));
        session.getMessages().add(action);
    }

    private void appendOffer(List<IrisSolutionChoice> choices) {
        var offer = message(IrisMessageSender.LLM, "Trying it yourself is worth it here.");
        offer.setId(10L);
        offer.addContent(solutionOffer(session.getMode(), session.getEntityId(), choices));
        session.getMessages().add(offer);
    }

    private static IrisMessage message(IrisMessageSender sender, String text) {
        var message = new IrisMessage();
        message.setSender(sender);
        message.addContent(new IrisTextMessageContent(text));
        return message;
    }

    @Test
    void deletedOrWrongTypeSessionsFailBeforePipelineExecution() {
        assertThatThrownBy(() -> service.execute(session, Optional.empty(), Optional.empty(), Optional.empty(), Map.of(), List.of()))
                .isInstanceOf(de.tum.cit.aet.artemis.core.exception.EntityNotFoundException.class);
        when(sessions.findByIdWithMessagesAndContents(4L)).thenReturn(new IrisTutorSuggestionSession(7L, user));
        assertThatThrownBy(() -> service.execute(session, Optional.empty(), Optional.empty(), Optional.empty(), Map.of(), List.of())).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Expected IrisChatSession");
        org.mockito.Mockito.verifyNoInteractions(pipelines);
    }
}
