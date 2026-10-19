package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.dto.GitRepositoryAccessDTO;
import de.tum.cit.aet.artemis.programming.service.RepositoryAccessService;
import de.tum.cit.aet.artemis.programming.web.repository.RepositoryActionType;

/**
 * The git request path authorizes against the course the projection names, and for an exam exercise that is the course
 * of its exam rather than a course named on the exercise itself.
 * <p>
 * The two were only distinguishable on a row carrying both, which the database now refuses:
 * {@code CHECK_EXERCISE_COURSE_OR_EXERCISE_GROUP} makes an exercise belong to a course or to an exercise group and
 * never to both, so the case this class was written around is unreachable. What is left to hold is the ordinary path,
 * that a student of the exam's course reaches the repository through the projection.
 */
class GitRepositoryAccessCourseResolutionIntegrationTest extends AbstractProgrammingIntegrationIndependentTest {

    private static final String TEST_PREFIX = "gitaccesscourse";

    @Autowired
    private RepositoryAccessService repositoryAccessService;

    private ProgrammingExercise examExercise;

    private Course examCourse;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        examExercise = programmingExerciseUtilService.addCourseExamExerciseGroupWithOneProgrammingExercise();
        examCourse = examExercise.getExerciseGroup().getExam().getCourse();
    }

    /**
     * The counterpart, so the test above cannot pass because nobody can reach the repository: the same student, enrolled
     * in the exam's course, is allowed to read it.
     */
    @Test
    void studentOfTheExamCourseMayAccessTheRepository() {
        String login = TEST_PREFIX + "student1";
        User student = userUtilService.addStudentToCourse(login, examCourse);
        ProgrammingExerciseStudentParticipation participation = participationUtilService.addStudentParticipationForProgrammingExercise(examExercise, login);

        // A student reaches the repository only once the exam has started, so this has to be true for the control to
        // say anything about the course.
        Exam exam = examExercise.getExerciseGroup().getExam();
        exam.setStartDate(ZonedDateTime.now().minusHours(1));
        examRepository.save(exam);

        GitRepositoryAccessDTO projection = programmingExerciseRepository.findAccessProjectionByProjectKey(examExercise.getProjectKey()).getFirst();

        assertThatCode(() -> repositoryAccessService.checkAccessRepositoryElseThrow(participation, student, projection, RepositoryActionType.READ))
                .as("membership in the exam's course does grant access").doesNotThrowAnyException();
    }
}
