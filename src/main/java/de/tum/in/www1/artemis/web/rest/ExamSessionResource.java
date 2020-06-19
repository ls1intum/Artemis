package de.tum.in.www1.artemis.web.rest;

import de.tum.in.www1.artemis.domain.exam.ExamSession;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.service.ExamSessionService;
import de.tum.in.www1.artemis.service.StudentExamService;
import de.tum.in.www1.artemis.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * REST controller for managing ExamSessions.
 */
@RestController
@RequestMapping("/api")
public class ExamSessionResource {

    private final Logger log = LoggerFactory.getLogger(ExamSessionResource.class);

    private final ExamSessionService examSessionService;

    private final UserService userService;

    private final StudentExamService studentExamService;

    public ExamSessionResource(ExamSessionService examSessionService, UserService userService, StudentExamService studentExamService) {
        this.examSessionService = examSessionService;
        this.userService = userService;
        this.studentExamService = studentExamService;

    }

    /**
     * GET /courses/{courseId}/exams/{examId}/currentSession : Get current exam session for given exam and user
     *
     * @param courseId  the course to which the student exams belong to
     * @param examId    the exam to which the student exams belong to
     * @return the ResponseEntity with status 200 (OK) and the token as string
     */
    @GetMapping("/courses/{courseId}/exams/{examId}/currentSession")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ExamSession> getCurrentExamSession(@PathVariable Long courseId, @PathVariable Long examId) {
        var userId = userService.getUser().getId();
        log.debug("REST request to get the current exam session for user : {} in the given exam : {}", userId, examId);

        StudentExam studentExam = studentExamService.findOneByExamIdAndUserId(examId, userId);

        ExamSession examSession = examSessionService.getCurrentExamSession(studentExam);
        return ResponseEntity.ok(examSession);
    }

    /**
     * GET /courses/{courseId}/exams/{examId}/newSession : Create exam session for given exam and user
     *
     * @param courseId  the course to which the student exams belong to
     * @param examId    the exam to which the student exams belong to
     * @return the ResponseEntity with status 200 (OK) and the token as string
     */
    @GetMapping("/courses/{courseId}/exams/{examId}/newSession")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ExamSession> createExamSession(@PathVariable Long courseId, @PathVariable Long examId) {
        var userId = userService.getUser().getId();
        log.debug("REST request to create exam session for user : {} in the given exam : {}", userId, examId);

        String sessionToken = generateSafeToken();

        StudentExam studentExam = studentExamService.findOneByExamIdAndUserId(examId, userId);

        examSessionService.deleteExamSession(studentExam);

        ExamSession examSession = new ExamSession();
        examSession.setSessionToken(sessionToken);
        examSession.setStudentExam(studentExam);
        // TODO set other attributes like fingerprint and user agent

        examSessionService.saveExamSession(examSession);

        return ResponseEntity.ok(examSession);
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
