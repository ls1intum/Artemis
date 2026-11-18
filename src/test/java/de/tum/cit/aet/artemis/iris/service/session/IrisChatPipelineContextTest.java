package de.tum.cit.aet.artemis.iris.service.session;

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
import org.mockito.ArgumentCaptor;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisTutorSuggestionSession;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettings;
import de.tum.cit.aet.artemis.iris.dto.IrisCombinedViewContextDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisMessageContextDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisSlidesContextDTO;
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
        service = new IrisChatPipelineExecutionService(sessions, courses, exercises, programmingExercises, mock(ProgrammingExerciseStudentParticipationRepository.class),
                mock(ProgrammingSubmissionRepository.class), null, Optional.empty(), Optional.of(lectures), null, dtoService, pipelines, authCheck);
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
        service.execute(session, Optional.empty(), Optional.of(IrisCourseSettings.defaultSettings()), Optional.empty(), Map.of(), List.of(), "tab");
        var builder = ArgumentCaptor.forClass(PyrisPipelineService.ChatPipelineDTOBuilder.class);
        verify(pipelines, atLeastOnce()).executeChatPipeline(any(), any(), any(), any(), any(), builder.capture());
        return builder.getValue().apply(new PyrisPipelineExecutionDTO(null), user, null);
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
