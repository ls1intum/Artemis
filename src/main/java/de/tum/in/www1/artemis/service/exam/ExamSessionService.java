package de.tum.in.www1.artemis.service.exam;

import java.security.SecureRandom;
import java.util.Base64;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.exam.ExamSession;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.repository.ExamSessionRepository;
import inet.ipaddr.IPAddress;

/**
 * Service Implementation for managing ExamSession.
 */
@Service
public class ExamSessionService {

    private final Logger log = LoggerFactory.getLogger(ExamSessionService.class);

    private final ExamSessionRepository examSessionRepository;

    public ExamSessionService(ExamSessionRepository examSessionRepository) {
        this.examSessionRepository = examSessionRepository;
    }

    /**
     * Creates and saves an exam session for given student exam
     *
     * @param studentExam student exam for which an exam session shall be created
     * @param fingerprint the browser fingerprint reported by the client, can be null
     * @param userAgent the user agent of the client, can be null
     * @param instanceId the instance id of the client, can be null
     * @param ipAddress the ip address of the client, can be null
     * @return the newly create exam session
     */
    public ExamSession startExamSession(StudentExam studentExam, @Nullable String fingerprint, @Nullable String userAgent, @Nullable String instanceId,
            @Nullable IPAddress ipAddress) {
        log.debug("Exam session started");
        String sessionToken = generateSafeToken();
        ExamSession examSession = new ExamSession();
        examSession.setSessionToken(sessionToken);
        examSession.setStudentExam(studentExam);
        examSession.setBrowserFingerprintHash(fingerprint);
        examSession.setUserAgent(userAgent);
        examSession.setInstanceId(instanceId);
        examSession.setIpAddress(ipAddress);
        examSession = examSessionRepository.save(examSession);
        return examSession;
    }

    private String generateSafeToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String token = encoder.encodeToString(bytes);
        return token.substring(0, 16);
    }

    /**
     * Checks the number of exam sessions for student exam and decides if the current session is initial or not
     *
     * @param studentExamId student exam id which is associated with exam session
     * @return true if there is only one exam session for this student exam in the database, otherwise returns false
     */
    public boolean checkExamSessionIsInitial(Long studentExamId) {
        long examSessionCount = examSessionRepository.findExamSessionCountByStudentExamId(studentExamId);
        return (examSessionCount == 1);
    }
}
