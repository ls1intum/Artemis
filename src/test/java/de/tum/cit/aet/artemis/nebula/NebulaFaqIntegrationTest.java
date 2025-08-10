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

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.nebula.architecture.AbstractNebulaIntegrationTest;
import de.tum.cit.aet.artemis.nebula.config.NebulaEnabled;
import de.tum.cit.aet.artemis.nebula.dto.FaqConsistencyDTO;
import de.tum.cit.aet.artemis.nebula.dto.FaqConsistencyResponse;
import de.tum.cit.aet.artemis.nebula.dto.FaqRewritingDTO;
import de.tum.cit.aet.artemis.nebula.dto.FaqRewritingResponse;
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
        FaqRewritingResponse potentialResponse = new FaqRewritingResponse("rewritten: " + toBeRewritten);
        var requestDTO = new FaqRewritingDTO(toBeRewritten, null);
        nebulaRequestMockProvider.mockFaqRewritingRequestReturning(req -> potentialResponse);
        FaqRewritingResponse response = request.postWithResponseBody("/api/nebula/courses/" + course.getId() + "/rewrite-text", requestDTO, FaqRewritingResponse.class,
                HttpStatus.OK);
        assertThat(potentialResponse.rewrittenText()).isEqualTo(response.rewrittenText());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void callConsistencyPipeline_shouldSucceed() throws Exception {
        var requestDTO = new FaqConsistencyDTO("test", null);
        FaqConsistencyResponse response = createFaqConsistencyResponse();
        nebulaRequestMockProvider.mockFaqConsistencyRequestReturning(req -> response);
        FaqConsistencyResponse actualRespones = request.postWithResponseBody("/api/nebula/courses/" + course.getId() + "/consistency-check", requestDTO,
                FaqConsistencyResponse.class, HttpStatus.OK);
        assertThat(actualRespones.consistent()).isEqualTo(response.consistent());
        assertThat(actualRespones.faqIds().size()).isEqualTo(response.faqIds().size());
        assertThat(actualRespones.faqIds()).containsExactlyElementsOf(response.faqIds());
        assertThat(actualRespones.improvement()).isEqualTo(response.improvement());
        assertThat(actualRespones.inconsistencies().size()).isEqualTo(response.inconsistencies().size());
        assertThat(actualRespones.inconsistencies()).containsExactlyElementsOf(response.inconsistencies());
    }

    private FaqConsistencyResponse createFaqConsistencyResponse() {
        List<String> inconstistencies = new ArrayList<>();
        inconstistencies.add("inconsistency 1");
        inconstistencies.add("inconsistency 2");
        String improvement = "Newly improved text";
        List<Long> faqIds = new ArrayList<>();
        faqIds.add(1L);
        faqIds.add(2L);
        FaqConsistencyResponse response = new FaqConsistencyResponse(false, inconstistencies, improvement, faqIds);
        return response;
    }
}
