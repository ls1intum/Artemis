package de.tum.in.www1.artemis.web.rest.iris;

import org.springframework.boot.actuate.health.Status;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastStudent;
import de.tum.in.www1.artemis.service.connectors.pyris.PyrisHealthIndicator;
import de.tum.in.www1.artemis.service.iris.IrisRateLimitService;

@Profile("iris")
@RestController
@RequestMapping("iris/")
public class IrisResource {

    protected final UserRepository userRepository;

    protected final IrisRateLimitService irisRateLimitService;

    protected final PyrisHealthIndicator pyrisHealthIndicator;

    public IrisResource(UserRepository userRepository, PyrisHealthIndicator pyrisHealthIndicator, IrisRateLimitService irisRateLimitService) {
        this.userRepository = userRepository;
        this.pyrisHealthIndicator = pyrisHealthIndicator;
        this.irisRateLimitService = irisRateLimitService;
    }

    /**
     * GET iris/sessions/{sessionId}/active: Retrieve if Iris is active and additional information about the rate limit
     *
     * @return the ResponseEntity with status 200 (OK) and the health status of Iris
     */
    @GetMapping("status")
    @EnforceAtLeastStudent
    public ResponseEntity<IrisStatusDTO> getStatus() {
        var user = userRepository.getUser();
        var health = pyrisHealthIndicator.health(true);
        var rateLimitInfo = irisRateLimitService.getRateLimitInformation(user);

        return ResponseEntity.ok(new IrisStatusDTO(health.getStatus() == Status.UP, rateLimitInfo));
    }

}
