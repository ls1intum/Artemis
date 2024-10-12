package de.tum.cit.aet.artemis.exam;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.dto.ExamDeletionSummaryDTO;
import de.tum.cit.aet.artemis.exam.service.ExamSessionService;
import de.tum.cit.aet.artemis.exam.test_repository.StudentExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.build.BuildJob;
import de.tum.cit.aet.artemis.programming.test_repository.BuildJobTestRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExamDeletionIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "examdeletion";

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private ExamSessionService examSessionService;

    @Autowired
    private StudentExamTestRepository studentExamRepository;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private BuildJobTestRepository buildJobRepository;

    private static final int NUMBER_OF_STUDENTS = 4;

    private static final int NUMBER_OF_TUTORS = 1;

    private Course course;

    private Exam exam;

    private ProgrammingExercise programmingExercise;

    private User student1;

    private User student2;

    private User student3;

    private User student4;

    @BeforeAll
    void setup() {
        // setup users
        userUtilService.addUsers(TEST_PREFIX, NUMBER_OF_STUDENTS, NUMBER_OF_TUTORS, 0, 1);

        student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        student2 = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        student3 = userUtilService.getUserByLogin(TEST_PREFIX + "student3");
        student4 = userUtilService.getUserByLogin(TEST_PREFIX + "student4");
        User instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");

        // reset courses
        course = courseUtilService.addEmptyCourse();

        exam = examUtilService.addExam(course);
        exam = examUtilService.registerUsersForExamAndSaveExam(exam, TEST_PREFIX, NUMBER_OF_STUDENTS);

        Channel channel = examUtilService.addExamChannel(exam, "exam1 channel");
        Post instructorPost = examUtilService.createPost(channel, "Instructor Post", instructor);
        examUtilService.createAnswerPost(instructorPost, "Student Answer", student1);
        examUtilService.createAnswerPost(instructorPost, "Instructor Answer", instructor);

        exam = examUtilService.addTextModelingProgrammingExercisesToExam(exam, true, true);
        programmingExercise = (ProgrammingExercise) exam.getExerciseGroups().stream().flatMap(exerciseGroup -> exerciseGroup.getExercises().stream())
                .filter(exercise -> ExerciseType.PROGRAMMING.equals(exercise.getExerciseType())).findFirst().orElseThrow();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeletionSummary() throws Exception {
        addStudentExam(student1, false, false);
        addStudentExam(student2, false, false);

        addStudentExam(student3, true, false);
        ProgrammingExerciseStudentParticipation participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, student3.getLogin());
        Result result = participationUtilService.createSubmissionAndResult(participation, 1L, true);
        createBuildJob(participation, result);

        addStudentExam(student4, true, true);

        var response = request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/deletion-summary", HttpStatus.OK, ExamDeletionSummaryDTO.class);
        assertThat(response.numberOfBuilds()).isEqualTo(1);
        assertThat(response.numberOfCommunicationPosts()).isEqualTo(1);
        assertThat(response.numberOfAnswerPosts()).isEqualTo(2);
        assertThat(response.numberNotStartedExams()).isEqualTo(2);
        assertThat(response.numberStartedExams()).isEqualTo(2);
        assertThat(response.numberSubmittedExams()).isEqualTo(1);
    }

    private void addStudentExam(User student, boolean isStarted, boolean isSubmitted) {
        StudentExam studentExam = examUtilService.addStudentExamWithUser(exam, student);
        examSessionService.startExamSession(studentExam, null, null, null, null);
        if (isStarted) {
            studentExam.setStartedAndStartDate(ZonedDateTime.now());
        }
        if (isSubmitted) {
            studentExam.setSubmissionDate(ZonedDateTime.now());
            studentExam.setSubmitted(true);
        }

        if (isStarted || isSubmitted) {
            studentExamRepository.save(studentExam);
        }
    }

    private void createBuildJob(Participation participation, Result result) {
        var buildJob = new BuildJob();
        buildJob.setExerciseId(programmingExercise.getId());
        buildJob.setCourseId(course.getId());
        buildJob.setParticipationId(participation.getId());
        buildJob.setResult(result);

        buildJobRepository.save(buildJob);
    }
}
