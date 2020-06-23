package de.tum.in.www1.artemis.service;

import java.security.SecureRandom;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.exam.ExamSession;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.repository.ExamSessionRepository;

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
     * Get the current exam session for a student exam
     *
     * @param studentExam StudentExam
     * @return the current exam session
     */
    public ExamSession getCurrentExamSession(StudentExam studentExam) {
        var id = studentExam.getId();
        return examSessionRepository.findByStudentExamId(id);
    }

    /**
     * Creates and saves an exam session for given student exam
     *
     * @param studentExam student exam for which an exam session shall be created
     */
    public ExamSession startExamSession(StudentExam studentExam) {

        String sessionToken = generateSafeToken();

        this.deleteExamSession(studentExam);

        ExamSession examSession = new ExamSession();
        examSession.setSessionToken(sessionToken);
        examSession.setStudentExam(studentExam);
        // TODO set other attributes like fingerprint and user agent

        examSessionRepository.save(examSession);

        return examSession;

    }

    /**
     * Delete an exam session
     *
     * @param studentExam StudentExam for which we want to delete all existing sessions
     */
    private void deleteExamSession(StudentExam studentExam) {
        var id = studentExam.getId();
        var examSession = examSessionRepository.findByStudentExamId(id);
        if (examSession != null) {
            examSessionRepository.delete(examSession);
        }
    }

    private String generateSafeToken() {
        SecureRandom random = new SecureRandom();
        byte bytes[] = new byte[16];
        random.nextBytes(bytes);
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String token = encoder.encodeToString(bytes);
        return token.substring(0, 16);
    }
}
