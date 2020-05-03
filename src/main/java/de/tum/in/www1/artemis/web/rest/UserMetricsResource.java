package de.tum.in.www1.artemis.web.rest;

import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class UserMetricsResource {

    private final SimpUserRegistry simpUserRegistry;

    public UserMetricsResource(SimpUserRegistry simpUserRegistry) {
        this.simpUserRegistry = simpUserRegistry;
    }

    /**
     * GET - number of current users using the application.
     * @return number of current users using the application.
     */
    @GetMapping("/management/usermetrics")
    @PreAuthorize("hasAnyRole('ADMIN')")
    public int retrieveNumberOfUsers() {
        return this.simpUserRegistry.getUserCount();
    }

}
