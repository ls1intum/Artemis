package de.tum.cit.aet.artemis.core.service.course;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.LLMServiceType;
import de.tum.cit.aet.artemis.core.domain.LLMTokenUsageRequest;
import de.tum.cit.aet.artemis.core.domain.LLMTokenUsageTrace;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.test_repository.LLMTokenUsageRequestTestRepository;
import de.tum.cit.aet.artemis.core.test_repository.LLMTokenUsageTraceTestRepository;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Integration tests for {@link CourseResetService#resetStudentData(long)}.
 * Verifies that resetting a course with LLM token usage data does not cause
 * foreign key constraint violations by ensuring child records (requests) are
 * deleted before parent records (traces).
 */
class CourseResetServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "courseresetservice";

    @Autowired
    private CourseResetService courseResetService;

    @Autowired
    private LLMTokenUsageTraceTestRepository llmTokenUsageTraceTestRepository;

    @Autowired
    private LLMTokenUsageRequestTestRepository llmTokenUsageRequestTestRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    private Course course;

    private User student;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
        course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testResetCourseWithLLMTokenUsageData_shouldNotViolateForeignKeyConstraint() {
        // Create a trace with an associated request (simulating real LLM usage)
        var trace = new LLMTokenUsageTrace();
        trace.setCourseId(course.getId());
        trace.setUserId(student.getId());
        trace.setServiceType(LLMServiceType.IRIS);
        trace = llmTokenUsageTraceTestRepository.save(trace);

        var request = new LLMTokenUsageRequest();
        request.setModel("gpt-4");
        request.setNumInputTokens(100);
        request.setNumOutputTokens(50);
        request.setCostPerMillionInputTokens(30.0f);
        request.setCostPerMillionOutputTokens(60.0f);
        request.setServicePipelineId("test-pipeline");
        request.setTrace(trace);
        llmTokenUsageRequestTestRepository.save(request);

        // Verify data exists before reset
        assertThat(llmTokenUsageTraceTestRepository.findAllByCourseId(course.getId())).hasSize(1);
        assertThat(llmTokenUsageRequestTestRepository.findAllByTraceCourseId(course.getId())).hasSize(1);

        // Reset the course — this should not throw a FK constraint violation
        courseResetService.resetStudentData(course.getId());

        // Verify both requests and traces are deleted
        assertThat(llmTokenUsageRequestTestRepository.findAllByTraceCourseId(course.getId())).isEmpty();
        assertThat(llmTokenUsageTraceTestRepository.findAllByCourseId(course.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testResetCourseWithMultipleLLMTokenUsageRequests_shouldDeleteAll() {
        // Create a trace with multiple requests
        var trace = new LLMTokenUsageTrace();
        trace.setCourseId(course.getId());
        trace.setUserId(student.getId());
        trace.setServiceType(LLMServiceType.IRIS);
        trace = llmTokenUsageTraceTestRepository.save(trace);

        for (int i = 0; i < 3; i++) {
            var request = new LLMTokenUsageRequest();
            request.setModel("gpt-4");
            request.setNumInputTokens(100 * (i + 1));
            request.setNumOutputTokens(50 * (i + 1));
            request.setCostPerMillionInputTokens(30.0f);
            request.setCostPerMillionOutputTokens(60.0f);
            request.setServicePipelineId("pipeline-" + i);
            request.setTrace(trace);
            llmTokenUsageRequestTestRepository.save(request);
        }

        assertThat(llmTokenUsageRequestTestRepository.findAllByTraceCourseId(course.getId())).hasSize(3);

        // Reset the course — this should not throw a FK constraint violation
        courseResetService.resetStudentData(course.getId());

        assertThat(llmTokenUsageRequestTestRepository.findAllByTraceCourseId(course.getId())).isEmpty();
        assertThat(llmTokenUsageTraceTestRepository.findAllByCourseId(course.getId())).isEmpty();
    }
}
