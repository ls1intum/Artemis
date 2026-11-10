package de.tum.cit.aet.artemis.course.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.admin.domain.LLMServiceType;
import de.tum.cit.aet.artemis.admin.domain.LLMTokenUsageRequest;
import de.tum.cit.aet.artemis.admin.domain.LLMTokenUsageTrace;
import de.tum.cit.aet.artemis.assessment.domain.PresentationAssessment;
import de.tum.cit.aet.artemis.assessment.domain.PresentationAssessmentInstance;
import de.tum.cit.aet.artemis.assessment.domain.PresentationAssessmentMode;
import de.tum.cit.aet.artemis.assessment.domain.PresentationGradingMethod;
import de.tum.cit.aet.artemis.assessment.domain.PresentationRubricCriterion;
import de.tum.cit.aet.artemis.assessment.domain.PresentationRubricLevel;
import de.tum.cit.aet.artemis.assessment.domain.PresentationRubricRating;
import de.tum.cit.aet.artemis.assessment.domain.PresentationRubricScale;
import de.tum.cit.aet.artemis.assessment.repository.PresentationAssessmentInstanceRepository;
import de.tum.cit.aet.artemis.assessment.repository.PresentationAssessmentRepository;
import de.tum.cit.aet.artemis.assessment.repository.PresentationRubricCriterionRepository;
import de.tum.cit.aet.artemis.assessment.repository.PresentationRubricLevelRepository;
import de.tum.cit.aet.artemis.assessment.repository.PresentationRubricRatingRepository;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.test_repository.ConversationParticipantTestRepository;
import de.tum.cit.aet.artemis.communication.util.ConversationUtilService;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.core.test_repository.LLMTokenUsageRequestTestRepository;
import de.tum.cit.aet.artemis.core.test_repository.LLMTokenUsageTraceTestRepository;
import de.tum.cit.aet.artemis.core.test_repository.UserCourseRoleTestRepository;
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

    @Autowired
    private UserCourseRoleTestRepository userCourseRoleTestRepository;

    @Autowired
    private PresentationAssessmentRepository presentationAssessmentRepository;

    @Autowired
    private PresentationAssessmentInstanceRepository presentationAssessmentInstanceRepository;

    @Autowired
    private PresentationRubricCriterionRepository presentationRubricCriterionRepository;

    @Autowired
    private PresentationRubricLevelRepository presentationRubricLevelRepository;

    @Autowired
    private PresentationRubricRatingRepository presentationRubricRatingRepository;

    private Course course;

    private User student;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
        course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExercise(TEST_PREFIX);
        student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
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
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
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
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testResetDeletesExamUsersAndConversationParticipantsButKeepsCourseAndInstructor() {
        Exercise exercise = course.getExercises().iterator().next();
        long courseId = course.getId();
        long exerciseId = exercise.getId();
        User instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");

        // Student data that must be deleted by the reset.
        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(exercise, TEST_PREFIX + "student1");
        Exam exam = examUtilService.registerUsersForExamAndSaveExam(examUtilService.addExam(course), TEST_PREFIX, 1); // registers student1 as an exam user
        long examId = exam.getId();
        Channel channel = conversationUtilService.createCourseWideChannel(course, TEST_PREFIX + "channel");
        conversationUtilService.addParticipantToConversation(channel, TEST_PREFIX + "student1");

        // A SECOND, unrelated course whose exam users and conversation participants must be untouched by resetting the
        // first course. This catches a regression that broadens the scope (e.g. an exam-user or participant delete that
        // is no longer keyed by course/exam id) and would otherwise silently destroy another course's data.
        Course otherCourse = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        Exam otherExam = examUtilService.registerUsersForExamAndSaveExam(examUtilService.addExam(otherCourse), TEST_PREFIX, 1);
        long otherExamId = otherExam.getId();
        Channel otherChannel = conversationUtilService.createCourseWideChannel(otherCourse, TEST_PREFIX + "otherchannel");
        conversationUtilService.addParticipantToConversation(otherChannel, TEST_PREFIX + "student1");

        // Everything is present before the reset.
        assertThat(examUserRepository.countByExamId(examId)).isEqualTo(1);
        assertThat(conversationParticipantRepository.findConversationParticipantByConversationIdAndUserId(channel.getId(), student.getId())).isPresent();
        assertThat(studentParticipationRepository.findById(participation.getId())).isPresent();
        // The instructor holds the instructor role in the course before the reset.
        assertThat(userCourseRoleTestRepository.existsByUser_IdAndCourse_IdAndRole(instructor.getId(), courseId, CourseRole.INSTRUCTOR)).isTrue();

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
        assertThat(userCourseRoleTestRepository.existsByUser_IdAndCourse_IdAndRole(instructor.getId(), courseId, CourseRole.INSTRUCTOR)).isTrue();

        // The unrelated course's exam users and conversation participants are untouched.
        assertThat(examUserRepository.countByExamId(otherExamId)).isEqualTo(1);
        assertThat(conversationParticipantRepository.findConversationParticipantByConversationIdAndUserId(otherChannel.getId(), student.getId())).isPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testResetDeletesPresentationAssessmentInstancesButKeepsDefinitions() {
        Course otherCourse = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        PresentationAssessment presentationAssessment = createPresentationAssessmentWithInstance(course, "Course presentation");
        PresentationAssessment otherPresentationAssessment = createPresentationAssessmentWithInstance(otherCourse, "Other course presentation");

        assertThat(presentationAssessmentInstanceRepository.countByPresentationAssessmentCourseId(course.getId())).isEqualTo(1);
        assertThat(presentationAssessmentInstanceRepository.countByPresentationAssessmentCourseId(otherCourse.getId())).isEqualTo(1);

        courseResetService.resetStudentData(course.getId());

        assertThat(presentationAssessmentRepository.findById(presentationAssessment.getId())).isPresent();
        assertThat(presentationAssessmentInstanceRepository.countByPresentationAssessmentCourseId(course.getId())).isZero();
        assertThat(presentationAssessmentInstanceRepository.countByPresentationAssessmentCourseId(otherCourse.getId())).isEqualTo(1);
        assertThat(presentationAssessmentRepository.findById(otherPresentationAssessment.getId())).isPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testResetDeletesRubricRatingsButKeepsTheRubric() {
        PresentationAssessment presentationAssessment = new PresentationAssessment();
        presentationAssessment.setCourse(course);
        presentationAssessment.setTitle("Rubric presentation");
        presentationAssessment.setGradingMethod(PresentationGradingMethod.RUBRIC);
        presentationAssessment = presentationAssessmentRepository.save(presentationAssessment);

        PresentationRubricCriterion criterion = new PresentationRubricCriterion();
        criterion.setPresentationAssessment(presentationAssessment);
        criterion.setName("Content");
        criterion.setWeight(100);
        criterion.setScale(PresentationRubricScale.THREE_POINT);
        criterion = presentationRubricCriterionRepository.save(criterion);
        PresentationRubricLevel highestLevel = null;
        for (int position = 0; position < PresentationRubricScale.THREE_POINT.getLevelCount(); position++) {
            PresentationRubricLevel level = new PresentationRubricLevel();
            level.setCriterion(criterion);
            level.setPosition(position);
            highestLevel = presentationRubricLevelRepository.save(level);
        }

        PresentationAssessmentInstance instance = new PresentationAssessmentInstance();
        instance.setPresentationAssessment(presentationAssessment);
        instance.setPresentationDate(ZonedDateTime.now().plusDays(1));
        instance.setLanguage("en");
        instance.setMode(PresentationAssessmentMode.IN_PERSON);
        instance.setRubricScore(100.0);
        instance.setStudent(student);
        instance = presentationAssessmentInstanceRepository.save(instance);
        PresentationRubricRating rating = new PresentationRubricRating();
        rating.setInstance(instance);
        rating.setCriterion(criterion);
        rating.setLevel(highestLevel);
        presentationRubricRatingRepository.save(rating);

        courseResetService.resetStudentData(course.getId());

        assertThat(presentationRubricRatingRepository.findAllByInstanceIdOrderByCriterionPosition(instance.getId())).isEmpty();
        assertThat(presentationRubricCriterionRepository.findAllByPresentationAssessmentIdOrderByPosition(presentationAssessment.getId()))
                .extracting(PresentationRubricCriterion::getName).containsExactly("Content");
        assertThat(presentationRubricLevelRepository.findAllByCriterionIdInOrderByPosition(List.of(criterion.getId()))).hasSize(3);
        // Without assessed instances, the structure of the rubric is no longer fixed
        assertThat(presentationAssessmentInstanceRepository.countByPresentationAssessmentIdAndRubricScoreIsNotNull(presentationAssessment.getId())).isZero();
    }

    private PresentationAssessment createPresentationAssessmentWithInstance(Course course, String title) {
        PresentationAssessment presentationAssessment = new PresentationAssessment();
        presentationAssessment.setCourse(course);
        presentationAssessment.setTitle(title);
        presentationAssessment.setGradingMethod(PresentationGradingMethod.SCORE);
        presentationAssessment.setMaxPoints(20.0);
        presentationAssessment = presentationAssessmentRepository.save(presentationAssessment);

        PresentationAssessmentInstance instance = new PresentationAssessmentInstance();
        instance.setPresentationAssessment(presentationAssessment);
        instance.setPresentationDate(ZonedDateTime.now().plusDays(1));
        instance.setLanguage("en");
        instance.setMode(PresentationAssessmentMode.IN_PERSON);
        instance.setLocation("Room 1");
        instance.setResultPoints(17.0);
        instance.setRemark("Strong presentation");
        instance.setStudent(student);
        presentationAssessmentInstanceRepository.save(instance);
        return presentationAssessment;
    }
}
