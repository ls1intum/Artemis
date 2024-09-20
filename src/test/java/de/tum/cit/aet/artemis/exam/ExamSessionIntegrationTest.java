package de.tum.cit.aet.artemis.exam;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.repository.ExamSessionRepository;
import de.tum.cit.aet.artemis.exam.service.ExamSessionService;
import de.tum.cit.aet.artemis.exam.test_repository.StudentExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import inet.ipaddr.IPAddressString;

class ExamSessionIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "examsessionintegration";

    @Autowired
    private StudentExamTestRepository studentExamRepository;

    @Autowired
    private ExamSessionRepository examSessionRepository;

    @Autowired
    private ExamSessionService examSessionService;

    @Autowired
    private ExamUtilService examUtilService;

    private StudentExam studentExam1;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        Course course1 = courseUtilService.addEmptyCourse();
        var student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        Exam exam1 = examUtilService.addActiveExamWithRegisteredUser(course1, student1);
        studentExam1 = examUtilService.addStudentExam(exam1);
        studentExam1.setUser(student1);
        studentExamRepository.save(studentExam1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testStartExamSession_asStudent() {
        String newSessionToken = examSessionService.startExamSession(studentExam1, null, null, null, null).getSessionToken();
        String newerSessionToken = examSessionService.startExamSession(studentExam1, null, null, null, null).getSessionToken();
        String currentSessionToken = examSessionService.startExamSession(studentExam1, null, null, null, null).getSessionToken();

        assertThat(currentSessionToken).isNotEqualTo(newSessionToken).isNotEqualTo(newerSessionToken);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void storeFingerprintOnStartExamSession_asStudent() {
        final Long id = examSessionService.startExamSession(studentExam1, "5b2cc274f6eaf3a71647e1f85358ce32", null, null, null).getId();

        final var examSessionById = examSessionRepository.findById(id);
        assertThat(examSessionById).isPresent();
        assertThat(examSessionById.get().getBrowserFingerprintHash()).isEqualTo("5b2cc274f6eaf3a71647e1f85358ce32");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void storeUserAgentOnStartExamSession_asStudent() {
        final Long id = examSessionService.startExamSession(studentExam1, null,
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_6) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.1.2 Safari/605.1.15", null, null).getId();

        final var examSessionById = examSessionRepository.findById(id);
        assertThat(examSessionById).isPresent();
        assertThat(examSessionById.get().getUserAgent())
                .isEqualTo("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_6) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/13.1.2 Safari/605.1.15");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void storeIPv4OnStartExamSession_asStudent() {
        final var ipAddress = new IPAddressString("192.0.2.235").getAddress();
        final Long id = examSessionService.startExamSession(studentExam1, null, null, null, ipAddress).getId();

        final var examSessionById = examSessionRepository.findById(id);
        assertThat(examSessionById).isPresent();
        assertThat(examSessionById.get().getIpAddressAsIpAddress().toCanonicalString()).isEqualTo("192.0.2.235");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void storeIPv6OnStartExamSession_asStudent() {
        final var ipAddress = new IPAddressString("2001:db8:0:0:0:8a2e:370:7334").getAddress();
        final Long id = examSessionService.startExamSession(studentExam1, null, null, null, ipAddress).getId();

        final var examSessionById = examSessionRepository.findById(id);
        assertThat(examSessionById).isPresent();
        assertThat(examSessionById.get().getIpAddressAsIpAddress().toCanonicalString()).isEqualTo("2001:db8::8a2e:370:7334");
    }

}
