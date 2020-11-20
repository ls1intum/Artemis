package de.tum.in.www1.artemis.web.rest;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang.time.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.PersistentAuditEvent;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.*;

/**
 * REST controller for managing Course.
 */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('ADMIN')")
public class StatisticsResource {

    private final Logger log = LoggerFactory.getLogger(StatisticsResource.class);

    private final UserRepository userRepository;

    private final PersistenceAuditEventRepository persistentAuditEventRepository;

    private final SubmissionRepository submissionRepository;

    private final ParticipationRepository participationRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    public StatisticsResource(UserRepository userRepository, PersistenceAuditEventRepository persistentAuditEventRepository, SubmissionRepository submissionRepository,
            ParticipationRepository participationRepository, StudentParticipationRepository studentParticipationRepository) {
        this.userRepository = userRepository;
        this.persistentAuditEventRepository = persistentAuditEventRepository;
        this.submissionRepository = submissionRepository;
        this.participationRepository = participationRepository;
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
        List<User> loggedUsers = new ArrayList<>();
        Date spanDate = DateUtils.addDays(new Date(), -((int) span));
        List<PersistentAuditEvent> auditEvents = persistentAuditEventRepository.findAll();
        List<User> users = userRepository.findAll();
        for (User user : users) {
            List<PersistentAuditEvent> ownAudits = auditEvents.stream().filter(audit -> audit.getPrincipal().equals(user.getLogin())).collect(Collectors.toList());
            for (PersistentAuditEvent auditEvent : ownAudits) {
                if (auditEvent.getAuditEventType().equals("AUTHENTICATION_SUCCESS") && auditEvent.getAuditEventDate().compareTo(spanDate.toInstant()) >= 0
                        && !(user.getLogin().contains("test")) && !loggedUsers.contains(user)) {
                    loggedUsers.add(user);
                }
            }
        }
        // System.out.println(loggedUsers);
        return ResponseEntity.ok(loggedUsers.size());
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
        List<User> users = new ArrayList<>();
        Date spanDate = DateUtils.addDays(new Date(), -((int) span));

        List<StudentParticipation> participations = studentParticipationRepository.findAll();
        for (StudentParticipation participation : participations) {
            final User[] user = { null };
            participation.getStudent().ifPresent(resp -> user[0] = resp);
            if (user[0] != null && !(user[0].getLogin().contains("test"))) {
                List<Submission> submissionsOfParticipation = submissionRepository.findAllByParticipationId(participation.getId());
                // submissions.addAll(submissionsOfParticipation.stream().filter(sub -> sub.getSubmissionDate().toInstant().compareTo(spanDate.toInstant()) >=
                // 0).collect(Collectors.toList()));
                for (Submission submission : submissionsOfParticipation) {
                    if (submission.getSubmissionDate().toInstant().compareTo(spanDate.toInstant()) >= 0 && !users.contains(user[0])) {
                        users.add(user[0]);
                    }
                }
            }
        }
        // System.out.println(submissions);
        return ResponseEntity.ok(users.size());
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
        Date spanDate = DateUtils.addDays(new Date(), -((int) span));
        List<Submission> submissions = submissionRepository.findAll();
        List<Submission> recentSubmission = submissions.stream()
                .filter(submission -> submission.getSubmissionDate() != null && submission.getSubmissionDate().toInstant().compareTo(spanDate.toInstant()) >= 0)
                .collect(Collectors.toList());
        return ResponseEntity.ok(recentSubmission.size());
    }

}
