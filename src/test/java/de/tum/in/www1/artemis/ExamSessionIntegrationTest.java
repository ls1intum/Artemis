package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.repository.ExamSessionRepository;
import de.tum.in.www1.artemis.repository.StudentExamRepository;
import de.tum.in.www1.artemis.service.ExamSessionService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;

public class ExamSessionIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    DatabaseUtilService database;

    @Autowired
    RequestUtilService request;

    @Autowired
    StudentExamRepository studentExamRepository;

    @Autowired
    ExamSessionRepository examSessionRepository;

    @Autowired
    ExamSessionService examSessionService;

    private List<User> users;

    private Course course1;

    private Exam exam1;

    private StudentExam studentExam1;

    @BeforeEach
    public void initTestCase() {
        users = database.addUsers(1, 1, 1);
        course1 = database.addEmptyCourse();
        exam1 = database.addActiveExamWithRegisteredUser(course1, users.get(0));
        studentExam1 = database.addStudentExam(exam1);
        studentExam1.setUser(users.get(0));
        studentExamRepository.save(studentExam1);
    }

    @AfterEach
    public void resetDatabase() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testStartExamSession_asStudent() {

        String newSessionToken = examSessionService.startExamSession(studentExam1).getSessionToken();
        String newerSessionToken = examSessionService.startExamSession(studentExam1).getSessionToken();
        String currentSessionToken = examSessionService.startExamSession(studentExam1).getSessionToken();

        assertThat(currentSessionToken).isNotEqualTo(newSessionToken);
        assertThat(currentSessionToken).isNotEqualTo(newerSessionToken);
    }

}
