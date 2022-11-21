package de.tum.in.www1.artemis.web.rest.admin;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.security.annotations.EnforceAdmin;

/**
 * REST controller for administrating Exam.
 */
@RestController
@RequestMapping("api/admin/")
public class AdminExamResource {

    private final Logger log = LoggerFactory.getLogger(AdminExamResource.class);

    private final ExamRepository examRepository;

    public AdminExamResource(ExamRepository examRepository) {
        this.examRepository = examRepository;
    }

    /**
     * GET /exams/upcoming : Find all current and upcoming exams.
     *
     * @return the ResponseEntity with status 200 (OK) and a list of exams.
     */
    @GetMapping("courses/upcoming-exams")
    @EnforceAdmin
    public ResponseEntity<List<Exam>> getCurrentAndUpcomingExams() {
        log.debug("REST request to get all upcoming exams");

        List<Exam> upcomingExams = examRepository.findAllCurrentAndUpcomingExams();
        return ResponseEntity.ok(upcomingExams);
    }
}
