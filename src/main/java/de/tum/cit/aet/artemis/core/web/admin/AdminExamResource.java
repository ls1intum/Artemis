package de.tum.cit.aet.artemis.core.web.admin;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;

/**
 * REST controller for administrating Exam.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/admin/")
public class AdminExamResource {

    private static final Logger log = LoggerFactory.getLogger(AdminExamResource.class);

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
