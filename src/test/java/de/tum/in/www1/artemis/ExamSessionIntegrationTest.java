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
import de.tum.in.www1.artemis.service.exam.ExamSessionService;
import inet.ipaddr.IPAddressString;

class ExamSessionIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private StudentExamRepository studentExamRepository;

    @Autowired
    private ExamSessionRepository examSessionRepository;

    @Autowired
    private ExamSessionService examSessionService;

    private StudentExam studentExam1;

    @BeforeEach
    void initTestCase() {
        List<User> users = database.addUsers(1, 1, 0, 1);
        Course course1 = database.addEmptyCourse();
        Exam exam1 = database.addActiveExamWithRegisteredUser(course1, users.get(0));
        studentExam1 = database.addStudentExam(exam1);
        studentExam1.setUser(users.get(0));
        studentExamRepository.save(studentExam1);
    }

    @AfterEach
    void resetDatabase() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testStartExamSession_asStudent() {
        String newSessionToken = examSessionService.startExamSession(studentExam1, null, null, null, null).getSessionToken();
        String newerSessionToken = examSessionService.startExamSession(studentExam1, null, null, null, null).getSessionToken();
        String currentSessionToken = examSessionService.startExamSession(studentExam1, null, null, null, null).getSessionToken();

        assertThat(currentSessionToken).isNotEqualTo(newSessionToken).isNotEqualTo(newerSessionToken);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void storeFingerprintOnStartExamSession_asStudent() {
        final Long id = examSessionService.startExamSession(studentExam1, "5b2cc274f6eaf3a71647e1f85358ce32", null, null, null).getId();

        final var examSessionById = examSessionRepository.findById(id);
        assertThat(examSessionById).isPresent();
        assertThat(examSessionById.get().getBrowserFingerprintHash()).isEqualTo("5b2cc274f6eaf3a71647e1f85358ce32");
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void storeUserAgentOnStartExamSession_asStudent() {
        final Long id = examSessionService.startExamSession(studentExam1, null,
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_6) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.1.2 Safari/605.1.15", null, null).getId();

        final var examSessionById = examSessionRepository.findById(id);
        assertThat(examSessionById).isPresent();
        assertThat(examSessionById.get().getUserAgent())
                .isEqualTo("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_6) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.1.2 Safari/605.1.15");
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void storeIPv4OnStartExamSession_asStudent() {
        final var ipAddress = new IPAddressString("192.0.2.235").getAddress();
        final Long id = examSessionService.startExamSession(studentExam1, null, null, null, ipAddress).getId();

        final var examSessionById = examSessionRepository.findById(id);
        assertThat(examSessionById).isPresent();
        assertThat(examSessionById.get().getIpAddress().toCanonicalString()).isEqualTo("192.0.2.235");
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void storeIPv6OnStartExamSession_asStudent() {
        final var ipAddress = new IPAddressString("2001:db8:0:0:0:8a2e:370:7334").getAddress();
        final Long id = examSessionService.startExamSession(studentExam1, null, null, null, ipAddress).getId();

        final var examSessionById = examSessionRepository.findById(id);
        assertThat(examSessionById).isPresent();
        assertThat(examSessionById.get().getIpAddress().toCanonicalString()).isEqualTo("2001:db8::8a2e:370:7334");
    }

}
