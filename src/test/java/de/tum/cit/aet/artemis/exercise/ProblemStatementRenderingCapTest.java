package de.tum.cit.aet.artemis.exercise;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;

import de.tum.cit.aet.artemis.exercise.dto.ProblemStatementRenderRequestDTO;
import de.tum.cit.aet.artemis.exercise.dto.RenderedProblemStatementDTO;
import de.tum.cit.aet.artemis.exercise.dto.TestFeedbackInputDTO;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentBatchTest;

@TestPropertySource(properties = "artemis.problem-statement-rendering.max-test-results=2")
class ProblemStatementRenderingCapTest extends AbstractSpringIntegrationIndependentBatchTest {

    private static final String TEST_PREFIX = "pscap";

    private static final String POST_URL = "/api/exercise/problem-statement/render";

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
    }

    private static List<TestFeedbackInputDTO> feedbacks(int count) {
        List<TestFeedbackInputDTO> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            list.add(new TestFeedbackInputDTO((long) i, "test" + i, true, null, null));
        }
        return list;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldAcceptExactlyTheConfiguredLimit() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("[task][A](<testid>1</testid>)", feedbacks(2), null, "en", false, false, true, null);

        RenderedProblemStatementDTO result = request.postWithResponseBody(POST_URL, body, RenderedProblemStatementDTO.class, HttpStatus.OK);

        assertThat(result.html()).contains("data-test-status=\"success\"");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRejectOneAboveTheConfiguredLimit() throws Exception {
        var body = new ProblemStatementRenderRequestDTO("[task][A](<testid>1</testid>)", feedbacks(3), null, "en", false, false, true, null);

        request.postWithoutResponseBody(POST_URL, body, HttpStatus.UNPROCESSABLE_CONTENT);
    }
}
