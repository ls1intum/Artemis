package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisChatStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisRunState;
import de.tum.cit.aet.artemis.iris.util.IrisChatSessionFactory;

/**
 * HTTP-contract tests for {@link de.tum.cit.aet.artemis.iris.web.internal.PyrisInternalStatusUpdateResource}, focused on the ask-user path alias
 * for the chat status endpoint and the shared run-id mismatch handling.
 */
class PyrisInternalStatusUpdateResourceTest extends AbstractIrisChatSessionTest {

    private static final String TEST_PREFIX = "pyrisinternalstatusupdate";

    @Autowired
    private PyrisJobService pyrisJobService;

    @Autowired
    private IrisSessionRepository irisSessionRepository;

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldAcceptStatusUpdateAndRemoveJobWhenPostedToAskUserPathAlias() throws Exception {
        String token = createChatJobForNewSession();

        request.postWithoutResponseBody("/api/iris/internal/pipelines/ask-user/runs/" + token + "/status",
                new PyrisChatStatusUpdateDTO("Done", PyrisRunState.FINISHED, null, null, null, null, null, null), HttpStatus.OK, authHeader(token));

        assertThat(pyrisJobService.getJob(token)).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldAcceptStatusUpdateWhenPostedToRegularChatPath() throws Exception {
        String token = createChatJobForNewSession();

        request.postWithoutResponseBody("/api/iris/internal/pipelines/chat/runs/" + token + "/status",
                new PyrisChatStatusUpdateDTO("Done", PyrisRunState.FINISHED, null, null, null, null, null, null), HttpStatus.OK, authHeader(token));

        assertThat(pyrisJobService.getJob(token)).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnConflictWhenRunIdInPathDoesNotMatchAuthenticatedJobOnAskUserPath() throws Exception {
        String token = createChatJobForNewSession();

        request.postWithoutResponseBody("/api/iris/internal/pipelines/ask-user/runs/some-other-run-id/status",
                new PyrisChatStatusUpdateDTO("Done", PyrisRunState.FINISHED, null, null, null, null, null, null), HttpStatus.CONFLICT, authHeader(token));

        // The mismatch must be rejected before any processing takes place, so the job is still tracked under its original token.
        assertThat(pyrisJobService.getJob(token)).isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnForbiddenWhenAuthorizationHeaderIsMissing() throws Exception {
        String token = createChatJobForNewSession();

        request.postWithoutResponseBody("/api/iris/internal/pipelines/ask-user/runs/" + token + "/status",
                new PyrisChatStatusUpdateDTO("Done", PyrisRunState.FINISHED, null, null, null, null, null, null), HttpStatus.FORBIDDEN, new HttpHeaders());
    }

    private String createChatJobForNewSession() {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        IrisChatSession session = irisSessionRepository.save(IrisChatSessionFactory.createProgrammingExerciseChatSessionForUser(programmingExercise, user));

        return pyrisJobService.addChatJob(course.getId(), session.getId(), programmingExercise.getId(), null);
    }

    private HttpHeaders authHeader(String token) {
        return new HttpHeaders(new LinkedMultiValueMap<>(Map.of(HttpHeaders.AUTHORIZATION, List.of(Constants.BEARER_PREFIX + token))));
    }
}
