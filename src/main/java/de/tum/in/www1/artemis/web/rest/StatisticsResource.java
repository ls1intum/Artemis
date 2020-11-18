package de.tum.in.www1.artemis.web.rest;

import java.util.*;

import org.apache.commons.lang.time.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.PersistentAuditEvent;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.PersistenceAuditEventRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.*;

/**
 * REST controller for managing Course.
 */
@RestController
@RequestMapping("/api")
@PreAuthorize("hasRole('ADMIN')")
public class StatisticsResource {

    private final UserRepository userRepository;

    private final Logger log = LoggerFactory.getLogger(ExerciseGroupResource.class);

    private final PersistenceAuditEventRepository persistentAuditEventRepository;

    public StatisticsResource(UserRepository userRepository, PersistenceAuditEventRepository persistentAuditEventRepository) {
        this.userRepository = userRepository;
        this.persistentAuditEventRepository = persistentAuditEventRepository;
    }

    /**
     * GET management/statistics : get the amount of logged in user in the last "span" days.
     *
     * @param span the period of which amount should be calculated
     * @return the ResponseEntity with status 200 (OK) and the amount of users in body, or status 404 (Not Found)
     */
    @GetMapping("/management/statistics")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Integer> getloggedUsers(@RequestParam long span) {
        log.debug("REST request to get user login count in the last {} days", span);
        List<User> loggedUsers = new ArrayList<>();
        Date spanDate = DateUtils.addDays(new Date(), -((int) span));
        List<PersistentAuditEvent> auditEvents = persistentAuditEventRepository.findAll();
        // List<PersistentAuditEvent> auditEvents = new ArrayList<>();
        System.out.println("auditEvents");
        System.out.println(auditEvents);
        List<User> users = userRepository.findAll();
        System.out.println("users");
        System.out.println(users.size());
        for (User user : users) {
            for (PersistentAuditEvent auditEvent : auditEvents) {
                if (auditEvent.getPrincipal().equals(user.getLogin())) {
                    if (auditEvent.getAuditEventType().equals("AUTHENTICATION_SUCCESS")) {
                        if (auditEvent.getAuditEventDate().compareTo(spanDate.toInstant()) == 0) {
                            if (!(user.getLogin().contains("test"))) {
                                if (!loggedUsers.contains(user)) {
                                    loggedUsers.add(user);
                                }
                            }
                        }
                    }
                }
            }
        }
        System.out.println(users.size());
        return ResponseEntity.ok(users.size());
    }
}
