package de.tum.cit.aet.artemis.programming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.dto.GitRepositoryAccessDTO;
import de.tum.cit.aet.artemis.programming.service.RepositoryAccessService;
import de.tum.cit.aet.artemis.programming.web.repository.RepositoryActionType;

/**
 * The git request path authorizes against the course the projection names, so which course that is decides who may read
 * and write a repository.
 * <p>
 * An exam exercise belongs to the course of its exam, and to no other, which is what
 * {@code Exercise#getCourseViaExerciseGroupOrCourseMember()} answers. An exercise that also names a course directly is
 * not something the API can produce - the create and update paths refuse one carrying both - so this persists the row
 * past them. It is worth persisting because it is the only row on which a course resolution that prefers the direct
 * course differs from one that follows the exercise group, and the difference is not a wrong id in a response: it is a
 * role check answered against a course the exercise does not belong to.
 */
class GitRepositoryAccessCourseResolutionIntegrationTest extends AbstractProgrammingIntegrationIndependentTest {

    private static final String TEST_PREFIX = "gitaccesscourse";

    @Autowired
    private RepositoryAccessService repositoryAccessService;

    private ProgrammingExercise examExercise;

    private Course examCourse;

    private Course unrelatedCourse;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        examExercise = programmingExerciseUtilService.addCourseExamExerciseGroupWithOneProgrammingExercise();
        examCourse = examExercise.getExerciseGroup().getExam().getCourse();
        unrelatedCourse = courseUtilService.addEmptyCourse();

        // Past the REST layer on purpose: checkCourseAndExerciseGroupExclusivity rejects an exercise carrying both.
        examExercise.setCourse(unrelatedCourse);
        examExercise = programmingExerciseRepository.save(examExercise);
    }

    /**
     * The student is enrolled in the course the exercise names directly and in no other. Authorization resolves the
     * exam's course instead, finds no membership there, and refuses - which is the same answer the entity path gives,
     * since it never consults the direct course of an exam exercise.
     */
    @Test
    void studentOfTheDirectlyNamedCourseAloneMayNotAccessTheRepository() {
        String login = TEST_PREFIX + "student1";
        User student = userUtilService.addStudentToCourse(login, unrelatedCourse);
        ProgrammingExerciseStudentParticipation participation = participationUtilService.addStudentParticipationForProgrammingExercise(examExercise, login);

        GitRepositoryAccessDTO projection = programmingExerciseRepository.findAccessProjectionByProjectKey(examExercise.getProjectKey()).getFirst();
        assertThat(projection.courseId()).as("authorization is decided against the exam's course").isEqualTo(examCourse.getId());

        assertThatExceptionOfType(AccessForbiddenException.class).as("membership in the directly named course alone does not grant access")
                .isThrownBy(() -> repositoryAccessService.checkAccessRepositoryElseThrow(participation, student, projection, RepositoryActionType.READ));
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
