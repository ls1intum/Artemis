package de.tum.cit.aet.artemis.hyperion;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_HYPERION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.hyperion.config.HyperionTestConfiguration;
import de.tum.cit.aet.artemis.hyperion.proto.InconsistencyCheckRequest;
import de.tum.cit.aet.artemis.hyperion.proto.ReviewAndRefineGrpc;
import de.tum.cit.aet.artemis.hyperion.proto.RewriteProblemStatementRequest;
import de.tum.cit.aet.artemis.hyperion.web.HyperionReviewAndRefineResource;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;

/**
 * Integration tests for Hyperion gRPC service using official grpc-spring patterns.
 */
@SpringBootTest(properties = { "grpc.server.inProcessName=test", "grpc.server.port=-1", "grpc.client.hyperion.address=in-process:test" })
@SpringJUnitConfig(classes = { HyperionTestConfiguration.class })
@ActiveProfiles({ "test", "artemis", "scheduling", "localci", "localvc", PROFILE_HYPERION })
@DirtiesContext
class HyperionReviewAndRefineIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "hyperiontest";

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @GrpcClient("hyperion")
    private ReviewAndRefineGrpc.ReviewAndRefineBlockingStub reviewAndRefineStub;

    private Long courseId;

    private Long exerciseId;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        courseId = course.getId();
        exerciseId = course.getExercises().iterator().next().getId();
    }

    // HTTP API Tests
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void checkConsistency_asInstructor_returnsNoInconsistencies() throws Exception {
        var response = request.postWithResponseBodyString("/api/hyperion/review-and-refine/exercises/" + exerciseId + "/check-consistency", null, HttpStatus.OK);
        assertThat(response).isEqualTo("No inconsistencies found");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void rewriteProblemStatement_asInstructor_returnsEnhancedText() throws Exception {
        var requestBody = new HyperionReviewAndRefineResource.RewriteProblemStatementRequestDTO("Simple algorithm task");
        var response = request.postWithResponseBodyString("/api/hyperion/review-and-refine/courses/" + courseId + "/rewrite-problem-statement", requestBody, HttpStatus.OK);
        assertThat(response).startsWith("Enhanced:");
        assertThat(response).contains("Simple algorithm task");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void checkConsistency_asTutor_isForbidden() throws Exception {
        request.postWithoutResponseBody("/api/hyperion/review-and-refine/exercises/" + exerciseId + "/check-consistency", null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void rewriteProblemStatement_withOversizedText_returnsInternalServerError() throws Exception {
        var requestBody = new HyperionReviewAndRefineResource.RewriteProblemStatementRequestDTO("a".repeat(10001));
        request.postWithoutResponseBody("/api/hyperion/review-and-refine/courses/" + courseId + "/rewrite-problem-statement", requestBody, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // Direct gRPC Tests
    @Test
    void directStubCall_withValidInput_returnsSuccessfulResponse() {
        var request = InconsistencyCheckRequest.newBuilder().setProblemStatement("Implement a Java program that sorts an array.").build();

        var response = reviewAndRefineStub.checkInconsistencies(request);

        assertThat(response).isNotNull();
        assertThat(response.getInconsistencies()).isEqualTo("No inconsistencies found");
    }

    @Test
    void directStubCall_withEmptyInput_throwsInvalidArgument() {
        var request = InconsistencyCheckRequest.newBuilder().setProblemStatement("").build();

        assertThatThrownBy(() -> reviewAndRefineStub.checkInconsistencies(request)).isInstanceOf(StatusRuntimeException.class).hasMessageContaining("INVALID_ARGUMENT");
    }

    @Test
    void directStubCall_withDeadline_throwsDeadlineExceeded() {
        var stubWithDeadline = reviewAndRefineStub.withDeadlineAfter(1, TimeUnit.MILLISECONDS);
        var request = InconsistencyCheckRequest.newBuilder().setProblemStatement("Test problem statement").build();

        assertThatThrownBy(() -> stubWithDeadline.checkInconsistencies(request)).isInstanceOf(StatusRuntimeException.class).hasMessageContaining("DEADLINE_EXCEEDED");
    }

    @Test
    void directStubCall_rewriteWithValidText_returnsEnhancedContent() {
        var request = RewriteProblemStatementRequest.newBuilder().setText("Create a calculator program.").build();

        var response = reviewAndRefineStub.rewriteProblemStatement(request);

        assertThat(response).isNotNull();
        assertThat(response.getRewrittenText()).startsWith("Enhanced:");
        assertThat(response.getRewrittenText()).contains("calculator program");
    }
}
