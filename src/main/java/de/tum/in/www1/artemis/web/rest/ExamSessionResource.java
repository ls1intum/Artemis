package de.tum.in.www1.artemis.web.rest;

import de.tum.in.www1.artemis.domain.exam.ExamSession;
import de.tum.in.www1.artemis.service.ExamSessionService;
import de.tum.in.www1.artemis.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for managing ExamSessions.
 */
@RestController
@RequestMapping("/api")
public class ExamSessionResource {

    private final Logger log = LoggerFactory.getLogger(ExamSessionResource.class);

    private final ExamSessionService examSessionService;

    private final UserService userService;

    public ExamSessionResource(ExamSessionService examSessionService, UserService userService) {
        this.examSessionService = examSessionService;
        this.userService = userService;

    }

    /**
     * GET /courses/{courseId}/exams/{examId}/sessionToken : Get current exam session for given exam and user
     *
     * @param courseId  the course to which the student exams belong to
     * @param examId    the exam to which the student exams belong to
     * @return the ResponseEntity with status 200 (OK) and the token as string
     */
    @GetMapping("/courses/{courseId}/exams/{examId}/sessionToken")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ExamSession> getCurrentExamSession(@PathVariable Long courseId, @PathVariable Long examId) {
        var userId = userService.getUser().getId();
        log.debug("REST request to get the current exam session for user : {} in the given exam : {}", userId, examId);

        ExamSession examSession = examSessionService.getCurrentExamSession(userId, examId);
        return ResponseEntity.ok(examSession);
    }
}
