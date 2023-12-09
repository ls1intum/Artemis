package de.tum.in.www1.artemis;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.exam.ExamUtilService;
import de.tum.in.www1.artemis.lecture.LectureUtilService;
import de.tum.in.www1.artemis.user.UserUtilService;

class DatabaseQueryCountTest extends AbstractSpringIntegrationIndependentTest {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private static final String TEST_PREFIX = "databasequerycount";

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private LectureUtilService lectureUtilService;

    private static final int NUMBER_OF_TUTORS = 1;

    @BeforeEach
    void setup() {
        participantScoreScheduleService.shutdown();
        userUtilService.addUsers(TEST_PREFIX, 1, NUMBER_OF_TUTORS, 0, 0);
        User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        student.setGroups(Set.of(TEST_PREFIX + "tumuser"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testExamQueryCount() throws Exception {
        StudentExam studentExam = examUtilService.addStudentExamForActiveExamWithUser(TEST_PREFIX + "student1");

        assertThatDb(() -> startWorkingOnExam(studentExam)).hasBeenCalledAtMostTimes(getStartWorkingOnExamExpectedTotalQueryCount());
        assertThatDb(() -> submitExam(studentExam)).hasBeenCalledAtMostTimes(getSubmitExamExpectedTotalQueryCount());
    }

    private StudentExam startWorkingOnExam(StudentExam studentExam) throws Exception {
        return request.get(
                "/api/courses/" + studentExam.getExam().getCourse().getId() + "/exams/" + studentExam.getExam().getId() + "/student-exams/" + studentExam.getId() + "/conduction",
                HttpStatus.OK, StudentExam.class);
    }

    private Void submitExam(StudentExam studentExam) throws Exception {
        request.postWithoutLocation("/api/courses/" + studentExam.getExam().getCourse().getId() + "/exams/" + studentExam.getExam().getId() + "/student-exams/submit", studentExam,
                HttpStatus.OK, null);
        return null;
    }

    private long getStartWorkingOnExamExpectedTotalQueryCount() {
        final int findUserWithGroupsAndAuthoritiesQueryCount = 1;
        final int findByIdWithExercisesQueryCount = 1;
        final int findExamByIdWithCourseQueryCount = 1;
        final int updateStudentExamQueryCount = 1;
        final int findStudentParticipationsByStudentExamWithSubmissionsResultQueryCount = 1;
        final int createExamSessionQueryCount = 1;
        final int findExamSessionCountByStudentExamIdQueryCount = 1;
        final int findQuizPoolByExamIdQueryCount = 1;
        return findUserWithGroupsAndAuthoritiesQueryCount + findByIdWithExercisesQueryCount + findExamByIdWithCourseQueryCount + updateStudentExamQueryCount
                + findStudentParticipationsByStudentExamWithSubmissionsResultQueryCount + createExamSessionQueryCount + findExamSessionCountByStudentExamIdQueryCount
                + findQuizPoolByExamIdQueryCount;
    }

    private long getSubmitExamExpectedTotalQueryCount() {
        final int findUserWithGroupsAndAuthoritiesQueryCount = 1;
        final int findStudentExamByIdWithExercisesQueryCount = 1;
        final int findExamSessionByStudentExamIdQueryCount = 1;
        final int updateStudentExamQueryCount = 1;
        final int findStudentParticipationsByStudentExamWithSubmissionsResultQueryCount = 1;
        return findUserWithGroupsAndAuthoritiesQueryCount + findStudentExamByIdWithExercisesQueryCount + findExamSessionByStudentExamIdQueryCount + updateStudentExamQueryCount
                + findStudentParticipationsByStudentExamWithSubmissionsResultQueryCount;
    }
}
