package de.tum.cit.aet.artemis.course.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.admin.domain.LLMServiceType;
import de.tum.cit.aet.artemis.admin.domain.LLMTokenUsageRequest;
import de.tum.cit.aet.artemis.admin.domain.LLMTokenUsageTrace;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.test_repository.ConversationParticipantTestRepository;
import de.tum.cit.aet.artemis.communication.util.ConversationUtilService;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.core.test_repository.LLMTokenUsageRequestTestRepository;
import de.tum.cit.aet.artemis.core.test_repository.LLMTokenUsageTraceTestRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.repository.ExamUserRepository;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
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

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private ExamUserRepository examUserRepository;

    @Autowired
    private ConversationUtilService conversationUtilService;

    @Autowired
    private ConversationParticipantTestRepository conversationParticipantRepository;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private StudentParticipationTestRepository studentParticipationRepository;

    @Autowired
    private CourseTestRepository courseRepository;

    @Autowired
    private ExerciseTestRepository exerciseRepository;

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

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testResetDeletesExamUsersAndConversationParticipantsButKeepsCourseAndInstructor() {
        Exercise exercise = course.getExercises().iterator().next();
        long courseId = course.getId();
        long exerciseId = exercise.getId();

        // Student data that must be deleted by the reset.
        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(exercise, TEST_PREFIX + "student1");
        Exam exam = examUtilService.registerUsersForExamAndSaveExam(examUtilService.addExam(course), TEST_PREFIX, 1); // registers student1 as an exam user
        long examId = exam.getId();
        Channel channel = conversationUtilService.createCourseWideChannel(course, TEST_PREFIX + "channel");
        conversationUtilService.addParticipantToConversation(channel, TEST_PREFIX + "student1");

        // Everything is present before the reset.
        assertThat(examUserRepository.countByExamId(examId)).isEqualTo(1);
        assertThat(conversationParticipantRepository.findConversationParticipantByConversationIdAndUserId(channel.getId(), student.getId())).isPresent();
        assertThat(studentParticipationRepository.findById(participation.getId())).isPresent();
        // The instructor is enrolled via the course instructor group before the reset.
        assertThat(userUtilService.getUserByLogin(TEST_PREFIX + "instructor1").getGroups()).contains(course.getInstructorGroupName());

        courseResetService.resetStudentData(courseId);

        // Exam users (identity/seating data, and their signature/photo files) and conversation participants (channel
        // membership) are deleted, and the student's participation is gone.
        assertThat(examUserRepository.countByExamId(examId)).isZero();
        assertThat(conversationParticipantRepository.findConversationParticipantByConversationIdAndUserId(channel.getId(), student.getId())).isEmpty();
        assertThat(studentParticipationRepository.findById(participation.getId())).isEmpty();

        // The course material survives: the course, its exercise, the exam and the channel structure remain, and the
        // instructor keeps access (instructors are never unenrolled by a reset).
        assertThat(courseRepository.findById(courseId)).isPresent();
        assertThat(exerciseRepository.findById(exerciseId)).isPresent();
        assertThat(userUtilService.getUserByLogin(TEST_PREFIX + "instructor1").getGroups()).contains(course.getInstructorGroupName());
    }
}
