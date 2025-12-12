package de.tum.cit.aet.artemis.iris.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.conversation.Conversation;
import de.tum.cit.aet.artemis.communication.test_repository.PostTestRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.iris.domain.session.IrisCourseChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisLectureChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisProgrammingExerciseChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisTextExerciseChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisTutorSuggestionSession;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettingsDTO;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisRateLimitConfiguration;
import de.tum.cit.aet.artemis.iris.dto.CourseIrisSettingsDTO;
import de.tum.cit.aet.artemis.iris.exception.IrisRateLimitExceededException;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.lecture.api.LectureRepositoryApi;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.text.api.TextRepositoryApi;

@ExtendWith(MockitoExtension.class)
class IrisRateLimitServiceTest {

    private static final long COURSE_ID = 42L;

    private static final long USER_ID = 7L;

    @Mock
    private IrisMessageRepository irisMessageRepository;

    @Mock
    private IrisSettingsService irisSettingsService;

    @Mock
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Mock
    private TextRepositoryApi textRepositoryApi;

    @Mock
    private LectureRepositoryApi lectureRepositoryApi;

    @Mock
    private PostTestRepository postRepository;

    private IrisRateLimitService rateLimitService;

    private User user;

    @BeforeEach
    void setUp() {
        rateLimitService = new IrisRateLimitService(irisMessageRepository, irisSettingsService, programmingExerciseRepository, Optional.of(textRepositoryApi),
                Optional.of(lectureRepositoryApi), Optional.of(postRepository));
        user = new User();
        user.setId(USER_ID);
    }

    @Test
    void getRateLimitInformation_usesCourseOverridesWhenPresent() {
        var effective = new IrisRateLimitConfiguration(5, 3);
        when(irisSettingsService.getCourseSettingsDTO(COURSE_ID))
                .thenReturn(new CourseIrisSettingsDTO(COURSE_ID, IrisCourseSettingsDTO.defaultSettings(), effective, IrisRateLimitConfiguration.empty()));
        when(irisMessageRepository.countLlmResponsesOfUserWithinTimeframe(eq(USER_ID), any(), any())).thenReturn(3);

        var info = rateLimitService.getRateLimitInformation(COURSE_ID, user);

        assertThat(info).isEqualTo(new IrisRateLimitService.IrisRateLimitInformation(3, 5, 3));
        verify(irisMessageRepository).countLlmResponsesOfUserWithinTimeframe(eq(USER_ID), any(), any());
    }

    @Test
    void getRateLimitInformation_unlimitedOverridesSkipCounting() {
        var effective = IrisRateLimitConfiguration.empty();
        when(irisSettingsService.getCourseSettingsDTO(COURSE_ID))
                .thenReturn(new CourseIrisSettingsDTO(COURSE_ID, IrisCourseSettingsDTO.defaultSettings(), effective, IrisRateLimitConfiguration.empty()));

        var info = rateLimitService.getRateLimitInformation(COURSE_ID, user);

        assertThat(info).isEqualTo(new IrisRateLimitService.IrisRateLimitInformation(0, -1, 0));
        verify(irisMessageRepository, never()).countLlmResponsesOfUserWithinTimeframe(anyLong(), any(), any());
    }

    @Test
    void checkRateLimitElseThrow_considersSessionCourseOverrides() {
        var course = new Course();
        course.setId(COURSE_ID);

        var exercise = new ProgrammingExercise();
        exercise.setId(99L);
        exercise.setCourse(course);

        var session = new IrisProgrammingExerciseChatSession(exercise, user);

        when(programmingExerciseRepository.findById(exercise.getId())).thenReturn(Optional.of(exercise));
        when(irisMessageRepository.countLlmResponsesOfUserWithinTimeframe(eq(USER_ID), any(), any())).thenReturn(1);

        var effective = new IrisRateLimitConfiguration(1, 1);
        when(irisSettingsService.getCourseSettingsDTO(COURSE_ID))
                .thenReturn(new CourseIrisSettingsDTO(COURSE_ID, IrisCourseSettingsDTO.defaultSettings(), effective, IrisRateLimitConfiguration.empty()));

        assertThatThrownBy(() -> rateLimitService.checkRateLimitElseThrow(session, user)).isInstanceOf(IrisRateLimitExceededException.class);
    }

    @Test
    void checkRateLimitElseThrow_resolvesCourseChatSession() {
        var course = new Course();
        course.setId(COURSE_ID);

        var session = new IrisCourseChatSession(course, user);

        when(irisMessageRepository.countLlmResponsesOfUserWithinTimeframe(eq(USER_ID), any(), any())).thenReturn(1);

        var effective = new IrisRateLimitConfiguration(1, 1);
        when(irisSettingsService.getCourseSettingsDTO(COURSE_ID))
                .thenReturn(new CourseIrisSettingsDTO(COURSE_ID, IrisCourseSettingsDTO.defaultSettings(), effective, IrisRateLimitConfiguration.empty()));

        assertThatThrownBy(() -> rateLimitService.checkRateLimitElseThrow(session, user)).isInstanceOf(IrisRateLimitExceededException.class);
        verify(irisSettingsService).getCourseSettingsDTO(COURSE_ID);
    }

    @Test
    void checkRateLimitElseThrow_resolvesTextExerciseChatSession() {
        var course = new Course();
        course.setId(COURSE_ID);

        var textExercise = new de.tum.cit.aet.artemis.text.domain.TextExercise();
        textExercise.setId(88L);
        textExercise.setCourse(course);

        var session = new IrisTextExerciseChatSession();
        session.setExerciseId(textExercise.getId());

        when(textRepositoryApi.findByIdElseThrow(textExercise.getId())).thenReturn(textExercise);
        when(irisMessageRepository.countLlmResponsesOfUserWithinTimeframe(eq(USER_ID), any(), any())).thenReturn(1);

        var effective = new IrisRateLimitConfiguration(1, 1);
        when(irisSettingsService.getCourseSettingsDTO(COURSE_ID))
                .thenReturn(new CourseIrisSettingsDTO(COURSE_ID, IrisCourseSettingsDTO.defaultSettings(), effective, IrisRateLimitConfiguration.empty()));

        assertThatThrownBy(() -> rateLimitService.checkRateLimitElseThrow(session, user)).isInstanceOf(IrisRateLimitExceededException.class);
        verify(irisSettingsService).getCourseSettingsDTO(COURSE_ID);
    }

    @Test
    void checkRateLimitElseThrow_resolvesLectureChatSession() {
        var course = new Course();
        course.setId(COURSE_ID);

        var lecture = new Lecture();
        lecture.setId(77L);
        lecture.setCourse(course);

        var session = new IrisLectureChatSession();
        session.setLectureId(lecture.getId());

        when(lectureRepositoryApi.findByIdElseThrow(lecture.getId())).thenReturn(lecture);
        when(irisMessageRepository.countLlmResponsesOfUserWithinTimeframe(eq(USER_ID), any(), any())).thenReturn(1);

        var effective = new IrisRateLimitConfiguration(1, 1);
        when(irisSettingsService.getCourseSettingsDTO(COURSE_ID))
                .thenReturn(new CourseIrisSettingsDTO(COURSE_ID, IrisCourseSettingsDTO.defaultSettings(), effective, IrisRateLimitConfiguration.empty()));

        assertThatThrownBy(() -> rateLimitService.checkRateLimitElseThrow(session, user)).isInstanceOf(IrisRateLimitExceededException.class);
        verify(irisSettingsService).getCourseSettingsDTO(COURSE_ID);
    }

    @Test
    void checkRateLimitElseThrow_resolvesTutorSuggestionSession() {
        var course = new Course();
        course.setId(COURSE_ID);

        var conversation = org.mockito.Mockito.mock(Conversation.class);
        when(conversation.getCourse()).thenReturn(course);

        var post = new Post();
        post.setId(66L);
        post.setConversation(conversation);

        var session = new IrisTutorSuggestionSession(post.getId(), user);

        when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
        when(irisMessageRepository.countLlmResponsesOfUserWithinTimeframe(eq(USER_ID), any(), any())).thenReturn(1);

        var effective = new IrisRateLimitConfiguration(1, 1);
        when(irisSettingsService.getCourseSettingsDTO(COURSE_ID))
                .thenReturn(new CourseIrisSettingsDTO(COURSE_ID, IrisCourseSettingsDTO.defaultSettings(), effective, IrisRateLimitConfiguration.empty()));

        assertThatThrownBy(() -> rateLimitService.checkRateLimitElseThrow(session, user)).isInstanceOf(IrisRateLimitExceededException.class);
        verify(irisSettingsService).getCourseSettingsDTO(COURSE_ID);
    }

    @Test
    void checkRateLimitElseThrow_handlesAbsentOptionalApis() {
        // Create a service with absent optional APIs
        var rateLimitServiceWithAbsentApis = new IrisRateLimitService(irisMessageRepository, irisSettingsService, programmingExerciseRepository, Optional.empty(), Optional.empty(),
                Optional.empty());

        // Text exercise session should fall back to application defaults when API is absent
        var textSession = new IrisTextExerciseChatSession();
        textSession.setExerciseId(88L);

        var applicationDefaults = new IrisRateLimitConfiguration(100, 24);
        when(irisSettingsService.getApplicationRateLimitDefaults()).thenReturn(applicationDefaults);
        when(irisMessageRepository.countLlmResponsesOfUserWithinTimeframe(eq(USER_ID), any(), any())).thenReturn(0);

        // Should not throw - falls back to application defaults without exceptions
        var info = rateLimitServiceWithAbsentApis.getRateLimitInformation(textSession, user);
        assertThat(info.rateLimit()).isEqualTo(100);
        verify(irisSettingsService).getApplicationRateLimitDefaults();
    }
}
