package de.tum.in.www1.artemis.web.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.enumeration.SpanType;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.*;

/**
 * REST controller for managing user statistics.
 */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('ADMIN')")
public class StatisticsResource {

    private final Logger log = LoggerFactory.getLogger(StatisticsResource.class);

    private final StatisticsService service;

    public StatisticsResource(StatisticsService service) {
        this.service = service;
    }

    /**
     * GET management/statistics/users : get the amount of logged in user in the last "span" days.
     *
     * @param span the period of which the amount should be calculated
     * @return the ResponseEntity with status 200 (OK) and the amount of users in body, or status 404 (Not Found)
     */
    @GetMapping("management/statistics/users")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Integer> getloggedUsers(@RequestParam long span) {
        log.debug("REST request to get user login count in the last {} days", span);
        return ResponseEntity.ok(this.service.getLoggedInUsers(span));
    }

    /**
     * GET management/statistics/activeUsers : get the amount of active users which made a submission in the last "span" days.
     *
     * @param span the period of which the amount should be calculated
     * @return the ResponseEntity with status 200 (OK) and the amount of submissions in body, or status 404 (Not Found)
     */
    @GetMapping("management/statistics/activeUsers")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Integer> getActiveUsers(@RequestParam long span) {
        log.debug("REST request to get total amount of active users in the last {} days", span);
        return ResponseEntity.ok(this.service.getActiveUsers(span));
    }

    /**
     * GET management/statistics/submissions : get the amount of submissions made in the last "span" days.
     *
     * @param span the period of which the amount should be calculated
     * @return the ResponseEntity with status 200 (OK) and the amount of submissions in body, or status 404 (Not Found)
     */
    @GetMapping("management/statistics/submissions")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Integer[]> getTotalSubmissions(@RequestParam SpanType span, @RequestParam Integer periodIndex) {
        log.debug("REST request to get amount of submission in the last {} days", span);
        return ResponseEntity.ok(this.service.getTotalSubmissions(span, periodIndex));
    }

    /**
     * GET management/statistics/releasedExercises : get the amount of released exercises in the last "span" days.
     *
     * @param span the period of which the amount should be calculated
     * @return the ResponseEntity with status 200 (OK) and the amount of exercises in body, or status 404 (Not Found)
     */
    @GetMapping("management/statistics/releasedExercises")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Integer> getReleasedExercises(@RequestParam long span) {
        log.debug("REST request to get amount of released exercises in the last {} days", span);
        return ResponseEntity.ok(this.service.getReleasedExercises(span));
    }

    /**
     * GET management/statistics/releasedExercises : get the amount of exercises with due date in the last "span" days.
     *
     * @param span the period of which the amount should be calculated
     * @return the ResponseEntity with status 200 (OK) and the amount of exercises in body, or status 404 (Not Found)
     */
    @GetMapping("management/statistics/exerciseDeadlines")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Integer> getExercisesDeadlines(@RequestParam long span) {
        log.debug("REST request to get amount of exercises with a due date in the last {} days", span);
        return ResponseEntity.ok(this.service.getExerciseDeadlines(span));
    }

    /**
     * GET management/statistics/conductedExams : get the amount of conducted exams in the last "span" days.
     *
     * @param span the period of which the amount should be calculated
     * @return the ResponseEntity with status 200 (OK) and the amount of exams in body, or status 404 (Not Found)
     */
    @GetMapping("management/statistics/conductedExams")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Integer> getConductedExams(@RequestParam long span) {
        log.debug("REST request to get amount of conducted exams in the last {} days", span);
        return ResponseEntity.ok(this.service.getConductedExams(span));
    }

    /**
     * GET management/statistics/examParticipations : get the amount of exam participations in the last "span" days.
     *
     * @param span the period of which the amount should be calculated
     * @return the ResponseEntity with status 200 (OK) and the amount of participations in body, or status 404 (Not Found)
     */
    @GetMapping("management/statistics/examParticipations")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Integer> getExamParticipations(@RequestParam long span) {
        log.debug("REST request to get amount of exam participations in the last {} days", span);
        return ResponseEntity.ok(this.service.getExamParticipations(span));
    }

    /**
     * GET management/statistics/examRegistrations : get the amount of exam registrations in the last "span" days.
     *
     * @param span the period of which the amount should be calculated
     * @return the ResponseEntity with status 200 (OK) and the amount of registrations in body, or status 404 (Not Found)
     */
    @GetMapping("management/statistics/examRegistrations")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Integer> getExamRegistrations(@RequestParam long span) {
        log.debug("REST request to get amount of exam registrations in the last {} days", span);
        return ResponseEntity.ok(this.service.getExamRegistrations(span));
    }

    /**
     * GET management/statistics/activeTutors : get the amount of tutors who created an assessment in the last *span* days
     *
     * @param span the period of which the amount should be calculated
     * @return the ResponseEntity with status 200 (OK) and the amount of tutors in body, or status 404 (Not Found)
     */
    @GetMapping("management/statistics/activeTutors")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Integer> getActiveTutors(@RequestParam long span) {
        log.debug("REST request to get amount of tutors who created an assessment in the last {} days", span);
        return ResponseEntity.ok(this.service.getActiveTutors(span));
    }

    /**
     * GET management/statistics/createdResults : get the amount of created results in the last "span" days.
     *
     * @param span the period of which the amount should be calculated
     * @return the ResponseEntity with status 200 (OK) and the amount of results in body, or status 404 (Not Found)
     */
    @GetMapping("management/statistics/createdResults")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Integer> getCreatedResults(@RequestParam long span) {
        log.debug("REST request to get amount of created results in the last {} days", span);
        return ResponseEntity.ok(this.service.getCreatedResults(span));
    }

    /**
     * GET management/statistics/resultFeedbacks : get the amount of feedback created for the results in the last "span" days.
     *
     * @param span the period of which the amount should be calculated
     * @return the ResponseEntity with status 200 (OK) and the amount of feedbacks in body, or status 404 (Not Found)
     */
    @GetMapping("management/statistics/resultFeedbacks")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Integer> getResultFeedbacks(@RequestParam long span) {
        log.debug("REST request to get amount of feedbacks for the results in the last {} days", span);
        return ResponseEntity.ok(this.service.getResultFeedbacks(span));
    }

}
