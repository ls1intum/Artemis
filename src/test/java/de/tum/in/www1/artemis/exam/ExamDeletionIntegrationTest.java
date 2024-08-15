package de.tum.in.www1.artemis.exam;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.domain.BuildJob;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseType;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.BuildJobRepository;
import de.tum.in.www1.artemis.repository.StudentExamRepository;
import de.tum.in.www1.artemis.service.exam.ExamSessionService;
import de.tum.in.www1.artemis.web.rest.dto.ExamDeletionSummaryDTO;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExamDeletionIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "examdeletion";

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private ExamSessionService examSessionService;

    @Autowired
    private StudentExamRepository studentExamRepository;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private BuildJobRepository buildJobRepository;

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
