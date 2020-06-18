package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.exam.ExamSession;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
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
     * Get the current exam session for a student exam
     *
     * @param studentExam StudentExam
     * @return the current exam session
     */
    public ExamSession getCurrentExamSession(StudentExam studentExam) {
        log.debug("REST request to get the current exam session for student exam : {}", studentExam);
        var id = studentExam.getId();
        return examSessionRepository.findByStudentExamId(id);
    }

    public void saveExamSession(ExamSession examSession) {
        examSessionRepository.save(examSession);
    }

    public void deleteExamSession(StudentExam studentExam) {
        var id = studentExam.getId();
        var examSession = examSessionRepository.findByStudentExamId(id);
        examSessionRepository.delete(examSession);
    }
}
