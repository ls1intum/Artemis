package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.iris.domain.session.IrisCourseChatSession;
import de.tum.cit.aet.artemis.iris.repository.IrisCourseChatSessionRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.service.IrisRateLimitService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisPipelineService;
import de.tum.cit.aet.artemis.iris.service.session.IrisCourseChatSessionService;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisChatWebsocketService;

@ExtendWith(MockitoExtension.class)
class IrisCourseChatSessionServiceTest {

    @Mock
    private IrisMessageService irisMessageService;

    @Mock
    private IrisMessageRepository irisMessageRepository;

    @Mock
    private LLMTokenUsageService llmTokenUsageService;

    @Mock
    private IrisSettingsService irisSettingsService;

    @Mock
    private IrisChatWebsocketService irisChatWebsocketService;

    @Mock
    private AuthorizationCheckService authCheckService;

    @Mock
    private IrisSessionRepository irisSessionRepository;

    @Mock
    private IrisRateLimitService irisRateLimitService;

    @Mock
    private IrisCourseChatSessionRepository irisCourseChatSessionRepository;

    @Mock
    private PyrisPipelineService pyrisPipelineService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private IrisCourseChatSessionService irisCourseChatSessionService;

    private Course course;

    private User user;

    private IrisCourseChatSession session;

    @BeforeEach
    void setUp() {
        course = new Course();
        course.setId(42L);

        user = new User();
        user.setId(99L);
        user.setExternalLLMUsageAcceptedTimestamp(ZonedDateTime.now());

        session = new IrisCourseChatSession();
        session.setId(7L);
        session.setCourseId(course.getId());
        session.setUserId(user.getId());
    }

    @Test
    void checkHasAccessTo_allowsSessionOwner() {
        when(courseRepository.findByIdElseThrow(course.getId())).thenReturn(course);

        assertThatNoException().isThrownBy(() -> irisCourseChatSessionService.checkHasAccessTo(user, session));

        verify(courseRepository).findByIdElseThrow(course.getId());
        verify(authCheckService).checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);
    }

    @Test
    void checkHasAccessTo_throwsForDifferentUser() {
        when(courseRepository.findByIdElseThrow(course.getId())).thenReturn(course);
        session.setUserId(123L);

        assertThatThrownBy(() -> irisCourseChatSessionService.checkHasAccessTo(user, session)).isInstanceOf(AccessForbiddenException.class);

        verify(authCheckService).checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);
    }
}
