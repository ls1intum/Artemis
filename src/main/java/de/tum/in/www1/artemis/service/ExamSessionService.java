package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.exam.ExamSession;
import de.tum.in.www1.artemis.repository.ExamSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;


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
     * Get the current exam session for a specific user in the given exam.
     *
     * @param userId the id of the user
     * @param examId the id of the exam
     * @return the current session token
     */
    public ExamSession getCurrentExamSession(Long userId, Long examId) {
        log.debug("REST request to get the current exam session for user : {} in the given exam : {}", userId, examId);
        return examSessionRepository.getCurrentExamSessionByUserIdAndExamId(userId, examId);
    }
}
