package de.tum.cit.aet.artemis.hyperion.resource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.hyperion.dto.RewriteFaqRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.RewriteFaqResponseDTO;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class HyperionFaqRewriteResourceIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "hyperionfaqrewriteresource";

    @Autowired
    private UserUtilService userUtilService;

    private Long courseId;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        var course = courseUtilService.addEmptyCourse();
        courseId = course.getId();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testRewriteFaq_Success() throws Exception {
        RewriteFaqRequestDTO faqRequest = new RewriteFaqRequestDTO("Old FAQ Text");

        RewriteFaqResponseDTO result = request.postWithResponseBody("/api/hyperion/courses/" + courseId + "/faq/rewrite", faqRequest, RewriteFaqResponseDTO.class, HttpStatus.OK);
        assertThat(result).isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testRewriteFaq_ForbiddenForStudent() throws Exception {
        RewriteFaqRequestDTO faqRequest = new RewriteFaqRequestDTO("Some text");

        request.postWithResponseBody("/api/hyperion/courses/" + courseId + "/faq/rewrite", faqRequest, RewriteFaqResponseDTO.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testRewriteFaq_BadRequest_InvalidNullInput() throws Exception {
        RewriteFaqRequestDTO invalidFaqRequest = new RewriteFaqRequestDTO(null);

        request.postWithResponseBody("/api/hyperion/courses/" + courseId + "/faq/rewrite", invalidFaqRequest, RewriteFaqResponseDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testRewriteFaq_BadRequest_InvalidBlankInput() throws Exception {
        RewriteFaqRequestDTO invalidFaqRequest = new RewriteFaqRequestDTO("");

        request.postWithResponseBody("/api/hyperion/courses/" + courseId + "/faq/rewrite", invalidFaqRequest, RewriteFaqResponseDTO.class, HttpStatus.BAD_REQUEST);
    }
}
