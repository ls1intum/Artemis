package de.tum.in.www1.artemis.web.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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

    private final UserRepository userRepository;

    private final PersistenceAuditEventRepository persistentAuditEventRepository;

    private final SubmissionRepository submissionRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ExerciseRepository exerciseRepository;

    public StatisticsResource(StatisticsService service, UserRepository userRepository, PersistenceAuditEventRepository persistentAuditEventRepository,
            SubmissionRepository submissionRepository, ExerciseRepository exerciseRepository, StudentParticipationRepository studentParticipationRepository) {

        this.service = service;
        this.userRepository = userRepository;
        this.persistentAuditEventRepository = persistentAuditEventRepository;
        this.submissionRepository = submissionRepository;
        this.exerciseRepository = exerciseRepository;
        this.studentParticipationRepository = studentParticipationRepository;
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
    public ResponseEntity<Integer> getTotalSubmissions(@RequestParam long span) {
        log.debug("REST request to get amount of submission in the last {} days", span);
        return ResponseEntity.ok(this.service.getTotalSubmissions(span));
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

}
