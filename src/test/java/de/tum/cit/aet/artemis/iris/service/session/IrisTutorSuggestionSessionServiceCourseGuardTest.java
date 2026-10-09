package de.tum.cit.aet.artemis.iris.service.session;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.test_repository.PostTestRepository;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.iris.domain.session.IrisTutorSuggestionSession;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettings;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.service.IrisRateLimitService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisDTOService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisPipelineService;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisChatWebsocketService;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

/**
 * Unit tests for the guard against a post that does not belong to a course, without a Spring context.
 */
class IrisTutorSuggestionSessionServiceCourseGuardTest {

    private static final long POST_ID = 11L;

    private PostTestRepository postRepository;

    private IrisSessionRepository sessionRepository;

    private AuthorizationCheckService authCheckService;

    private IrisSettingsService settingsService;

    private IrisTutorSuggestionSessionService service;

    private User user;

    private IrisTutorSuggestionSession session;

    @BeforeEach
    void setUp() {
        postRepository = mock(PostTestRepository.class);
        sessionRepository = mock(IrisSessionRepository.class);
        authCheckService = mock(AuthorizationCheckService.class);
        settingsService = mock(IrisSettingsService.class);
        service = new IrisTutorSuggestionSessionService(sessionRepository, mock(IrisMessageRepository.class), mock(JsonMapper.class), mock(IrisMessageService.class),
                mock(IrisChatWebsocketService.class), mock(LLMTokenUsageService.class), mock(IrisRateLimitService.class), mock(PyrisPipelineService.class), authCheckService,
                settingsService, mock(ProgrammingExerciseTestRepository.class), mock(ProgrammingExerciseStudentParticipationRepository.class),
                mock(ProgrammingSubmissionRepository.class), mock(PyrisDTOService.class), postRepository, mock(UserTestRepository.class), mock(PyrisJobService.class));

        user = new User();
        user.setId(7L);
        session = new IrisTutorSuggestionSession(POST_ID, user);
        session.setId(3L);
        // a post that is neither part of a plagiarism case nor of a conversation has no course
        when(postRepository.findPostOrMessagePostByIdElseThrow(POST_ID)).thenReturn(new Post());
    }

    @Test
    void tokenUsageParametersFailIfThePostHasNoCourse() {
        var builder = mock(LLMTokenUsageService.LLMTokenUsageBuilder.class);

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> service.setLLMTokenUsageParameters(builder, session)).withMessageContaining("session 3");
    }

    @Test
    void accessCheckFailsIfThePostHasNoCourse() {
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> service.checkHasAccessTo(user, session)).withMessageContaining("session 3");
    }

    private Course stubPostWithCourse() {
        var course = new Course();
        course.setId(5L);
        var channel = new Channel();
        channel.setCourse(course);
        var post = new Post();
        post.setConversation(channel);
        when(postRepository.findPostOrMessagePostByIdElseThrow(POST_ID)).thenReturn(post);
        return course;
    }

    @Test
    void tokenUsageParametersUseTheCourseOfThePost() {
        stubPostWithCourse();
        var builder = mock(LLMTokenUsageService.LLMTokenUsageBuilder.class);

        service.setLLMTokenUsageParameters(builder, session);

        verify(builder).withCourse(5L);
    }

    @Test
    void accessCheckRequiresTeachingAssistantRoleInTheCourseOfThePost() {
        var course = stubPostWithCourse();
        user.setId(7L);

        assertThatCode(() -> service.checkHasAccessTo(user, session)).doesNotThrowAnyException();

        verify(authCheckService).checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, user);
    }

    @Test
    void requestAndHandleResponseResolvesTheCourseAndFailsIfIrisIsDisabled() {
        var course = stubPostWithCourse();
        when(sessionRepository.findByIdWithMessagesAndContents(3L)).thenReturn(session);
        when(settingsService.getSettingsForCourse(course)).thenReturn(new IrisCourseSettings(false, null, null, null, null, null, null));

        assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> service.requestAndHandleResponse(session, Optional.empty()));
    }
}
