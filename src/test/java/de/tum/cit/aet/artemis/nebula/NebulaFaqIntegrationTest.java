package de.tum.cit.aet.artemis.nebula;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.web.client.HttpStatusCodeException;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.nebula.architecture.AbstractNebulaIntegrationTest;
import de.tum.cit.aet.artemis.nebula.config.NebulaEnabled;
import de.tum.cit.aet.artemis.nebula.dto.FaqConsistencyDTO;
import de.tum.cit.aet.artemis.nebula.dto.FaqConsistencyResponseDTO;
import de.tum.cit.aet.artemis.nebula.dto.FaqRewritingDTO;
import de.tum.cit.aet.artemis.nebula.dto.FaqRewritingResponseDTO;
import de.tum.cit.aet.artemis.nebula.exception.NebulaConnectorException;
import de.tum.cit.aet.artemis.nebula.exception.NebulaException;
import de.tum.cit.aet.artemis.nebula.exception.NebulaForbiddenException;
import de.tum.cit.aet.artemis.nebula.exception.NebulaInternalErrorException;
import de.tum.cit.aet.artemis.nebula.service.FaqProcessingService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;

@Conditional(NebulaEnabled.class)
class NebulaFaqIntegrationTest extends AbstractNebulaIntegrationTest {

    private static final String TEST_PREFIX = "nebulafaqintegrationtest";

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private FaqProcessingService faqProcessingService;

    private Course course;

    @BeforeEach
    void initTestCase() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 1, 2);
        course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void callRewritingPipeline_shouldSucceed() throws Exception {
        String toBeRewritten = "Test";
        FaqRewritingResponseDTO potentialResponse = new FaqRewritingResponseDTO("rewritten: " + toBeRewritten);
        var requestDTO = new FaqRewritingDTO(toBeRewritten, null);
        nebulaRequestMockProvider.mockFaqRewritingRequestReturning(req -> potentialResponse);
        FaqRewritingResponseDTO response = request.postWithResponseBody("/api/nebula/courses/" + course.getId() + "/rewrite-text", requestDTO, FaqRewritingResponseDTO.class,
                HttpStatus.OK);
        assertThat(potentialResponse.rewrittenText()).isEqualTo(response.rewrittenText());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void callConsistencyPipeline_shouldSucceed() throws Exception {
        var requestDTO = new FaqConsistencyDTO("test", null);
        FaqConsistencyResponseDTO response = createFaqConsistencyResponse();
        nebulaRequestMockProvider.mockFaqConsistencyRequestReturning(req -> response);
        FaqConsistencyResponseDTO actualRespones = request.postWithResponseBody("/api/nebula/courses/" + course.getId() + "/consistency-check", requestDTO,
                FaqConsistencyResponseDTO.class, HttpStatus.OK);
        assertThat(actualRespones.consistent()).isEqualTo(response.consistent());
        assertThat(actualRespones.faqIds().size()).isEqualTo(response.faqIds().size());
        assertThat(actualRespones.faqIds()).containsExactlyElementsOf(response.faqIds());
        assertThat(actualRespones.improvement()).isEqualTo(response.improvement());
        assertThat(actualRespones.inconsistencies().size()).isEqualTo(response.inconsistencies().size());
        assertThat(actualRespones.inconsistencies()).containsExactlyElementsOf(response.inconsistencies());
    }

    private FaqConsistencyResponseDTO createFaqConsistencyResponse() {
        List<String> inconstistencies = new ArrayList<>();
        inconstistencies.add("inconsistency 1");
        inconstistencies.add("inconsistency 2");
        String improvement = "Newly improved text";
        List<Long> faqIds = new ArrayList<>();
        faqIds.add(1L);
        faqIds.add(2L);
        FaqConsistencyResponseDTO response = new FaqConsistencyResponseDTO(false, inconstistencies, improvement, faqIds);
        return response;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void callRewritingPipelineForbiddenShouldReturn403() throws Exception {

        var dto = new FaqRewritingDTO("test", null);
        nebulaRequestMockProvider.mockThrowingNebulaExceptionForUrl("/faq/rewrite-faq", new NebulaForbiddenException());
        request.postWithResponseBody("/api/nebula/courses/" + course.getId() + "/rewrite-text", dto, FaqRewritingResponseDTO.class, HttpStatus.UNAUTHORIZED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void callRewritingPipelineInternalErrorShouldReturn500() throws Exception {

        var dto = new FaqRewritingDTO("test", null);
        nebulaRequestMockProvider.mockThrowingNebulaExceptionForUrl("/faq/rewrite-faq", new NebulaInternalErrorException("simulated error"));

        request.postWithResponseBody("/api/nebula/courses/" + course.getId() + "/rewrite-text", dto, FaqRewritingResponseDTO.class, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void callRewritingPipelineCoonnectorErrorShouldReturn500() throws Exception {
        var dto = new FaqRewritingDTO("test", null);
        nebulaRequestMockProvider.mockThrowingNebulaRuntimeExceptionForUrl("/faq/rewrite-faq", new NebulaConnectorException("simulated connector failure"));
        request.postWithResponseBody("/api/nebula/courses/" + course.getId() + "/rewrite-text", dto, FaqRewritingResponseDTO.class, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testNebulaExceptionConvertion() throws Exception {
        String fallBackMessage = "An error within Nebula has occurred";
        NebulaException exception = this.nebulaConnectionService.toNebulaException(createHttpStatusCodeException(HttpStatus.FORBIDDEN, "Forbidden access to Nebula service"));
        // we do not check the message here, since we have default translation which cant be accessed by the server tests so it defaults to the fallback
        assertThat(exception).isInstanceOf(NebulaForbiddenException.class);
        assertThat(exception.getMessage()).isEqualTo(fallBackMessage);
        exception = this.nebulaConnectionService.toNebulaException(createHttpStatusCodeException(HttpStatus.UNAUTHORIZED, "Unauthorized access to Nebula service"));
        assertThat(exception).isInstanceOf(NebulaForbiddenException.class);
        assertThat(exception.getMessage()).isEqualTo(fallBackMessage);
        exception = this.nebulaConnectionService.toNebulaException(createHttpStatusCodeException(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error"));
        assertThat(exception).isInstanceOf(NebulaInternalErrorException.class);
        assertThat(exception.getMessage()).isEqualTo(fallBackMessage);
        exception = this.nebulaConnectionService.toNebulaException(createHttpStatusCodeException(HttpStatus.BAD_REQUEST, "Bad request"));
        assertThat(exception).isInstanceOf(NebulaInternalErrorException.class);
        assertThat(exception.getMessage()).isEqualTo(fallBackMessage);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testNebulaExceptionExtraction() throws Exception {
        HttpStatusCodeException httpException = createHttpStatusCodeException(HttpStatus.BAD_REQUEST, "Bad request");
        nebulaConnectionService.tryExtractErrorMessage(httpException);
        assertThat(httpException.getResponseBodyAsString()).isEqualTo("{\"message\": \"Bad request\"}");

    }

    private HttpStatusCodeException createHttpStatusCodeException(HttpStatus status, String message) {
        return new HttpStatusCodeException(status, message) {

            @Override
            public String getResponseBodyAsString() {
                return "{\"message\": \"" + message + "\"}";
            }
        };
    }
}
