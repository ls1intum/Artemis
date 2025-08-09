package de.tum.cit.aet.artemis.nebula;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.nebula.dto.FaqConsistencyDTO;
import de.tum.cit.aet.artemis.nebula.dto.FaqRewritingDTO;
import de.tum.cit.aet.artemis.nebula.service.FaqProcessingService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;

@Profile(PROFILE_IRIS)
class PyrisRewritingIntegrationTest extends AbstractNebulaIntegrationTest {

    private static final String TEST_PREFIX = "nebularewritingtest";

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
        // Wichtig: Stelle sicher, dass in AbstractNebulaIntegrationTest der Provider in @BeforeEach aktiviert wird:
        // nebulaRequestMockProvider.enableMockingOfRequests();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void callRewritingPipeline_shouldSucceed() throws Exception {
        var requestDTO = new FaqRewritingDTO("test", null);
        nebulaRequestMockProvider.mockFaqRewritingRequestReturning(req -> null);
        request.postWithoutResponseBody("/api/nebula/faq/" + course.getId() + "/rewrite-text", requestDTO, HttpStatus.OK);

        verify(faqProcessingService, atLeastOnce()).executeRewriting(any(), any(), any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void callConsistencyPipeline_shouldSucceed() throws Exception {
        var requestDTO = new FaqConsistencyDTO("test", null);
        nebulaRequestMockProvider.mockFaqConsistencyRequestReturning(req -> null);
        request.postWithoutResponseBody("/api/nebula/faq/" + course.getId() + "/check-consistency", requestDTO, HttpStatus.OK);
        verify(faqProcessingService, atLeastOnce()).executeConsistencyCheck(any(), any(), any());
    }
}
