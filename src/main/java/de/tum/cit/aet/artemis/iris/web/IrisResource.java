package de.tum.cit.aet.artemis.iris.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Status;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastInstructorInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastStudentInCourse;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.iris.dto.IngestionState;
import de.tum.cit.aet.artemis.iris.dto.IrisStatusDTO;
import de.tum.cit.aet.artemis.iris.service.IrisRateLimitService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisConnectorException;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisHealthIndicator;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisWebhookService;

@Profile(PROFILE_IRIS)
@Lazy
@RestController
@RequestMapping("api/iris/")
public class IrisResource {

    private static final Logger log = LoggerFactory.getLogger(IrisResource.class);

    protected final UserRepository userRepository;

    protected final IrisRateLimitService irisRateLimitService;

    protected final PyrisHealthIndicator pyrisHealthIndicator;

    private final AuthorizationCheckService authorizationCheckService;

    private final CourseRepository courseRepository;

    private final PyrisWebhookService pyrisWebhookService;

    public IrisResource(UserRepository userRepository, PyrisHealthIndicator pyrisHealthIndicator, IrisRateLimitService irisRateLimitService,
            AuthorizationCheckService authorizationCheckService, CourseRepository courseRepository, PyrisWebhookService pyrisWebhookService) {
        this.userRepository = userRepository;
        this.pyrisHealthIndicator = pyrisHealthIndicator;
        this.irisRateLimitService = irisRateLimitService;
        this.authorizationCheckService = authorizationCheckService;
        this.courseRepository = courseRepository;
        this.pyrisWebhookService = pyrisWebhookService;
    }

    /**
     * GET iris/courses/{courseId}/status: Retrieve if Iris is active and rate limit information for a specific course.
     *
     * @param courseId the ID of the course
     * @return the ResponseEntity with status 200 (OK) and the health status of Iris with course-specific rate limits
     */
    @GetMapping("courses/{courseId}/status")
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<IrisStatusDTO> getCourseStatus(@PathVariable long courseId) {
        var user = userRepository.getUser();
        var health = pyrisHealthIndicator.health(true);
        var rateLimitInfo = irisRateLimitService.getRateLimitInformation(courseId, user);

        return ResponseEntity.ok(new IrisStatusDTO(health.getStatus() == Status.UP, rateLimitInfo));
    }

    /**
     * Retrieves the ingestion state of a specific FAQ in a course by communicating with Pyris.
     *
     * <p>
     * This method sends a GET request to the external Pyris service to fetch the current ingestion
     * state of a FAQ, identified by its ID. It constructs a request using the provided
     * `courseId` and `faqId` and returns the state of the ingestion process (e.g., NOT_STARTED,
     * IN_PROGRESS, DONE, ERROR).
     * </p>
     *
     * @param courseId the ID of the course the FAQ belongs to
     * @param faqId    the ID of the FAQ for which the ingestion state is being requested
     * @return a {@link ResponseEntity} containing a map with the {@link IngestionState} of the FAQ,
     */
    @GetMapping("courses/{courseId}/faqs/{faqId}/ingestion-state")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<Map<Long, IngestionState>> getStatusOfFaqIngestion(@PathVariable long courseId, @PathVariable long faqId) {
        try {
            Map<Long, IngestionState> responseMap = Map.of(faqId, pyrisWebhookService.getFaqIngestionState(courseId, faqId));
            return ResponseEntity.ok(responseMap);
        }
        catch (PyrisConnectorException e) {
            log.error("Error fetching ingestion state for faq {}", faqId, e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

}
