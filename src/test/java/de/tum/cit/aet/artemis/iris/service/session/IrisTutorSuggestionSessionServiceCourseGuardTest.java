package de.tum.cit.aet.artemis.iris.service.session;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.test_repository.PostTestRepository;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.iris.domain.session.IrisTutorSuggestionSession;
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

    private IrisTutorSuggestionSessionService service;

    private User user;

    private IrisTutorSuggestionSession session;

    @BeforeEach
    void setUp() {
        postRepository = mock(PostTestRepository.class);
        service = new IrisTutorSuggestionSessionService(mock(IrisSessionRepository.class), mock(IrisMessageRepository.class), mock(JsonMapper.class),
                mock(IrisMessageService.class), mock(IrisChatWebsocketService.class), mock(LLMTokenUsageService.class), mock(IrisRateLimitService.class),
                mock(PyrisPipelineService.class), mock(AuthorizationCheckService.class), mock(IrisSettingsService.class), mock(ProgrammingExerciseTestRepository.class),
                mock(ProgrammingExerciseStudentParticipationRepository.class), mock(ProgrammingSubmissionRepository.class), mock(PyrisDTOService.class), postRepository,
                mock(UserTestRepository.class), mock(PyrisJobService.class));

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
}
