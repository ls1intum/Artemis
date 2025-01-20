package de.tum.cit.aet.artemis.iris.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastInstructorInCourse;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.iris.dto.IngestionState;
import de.tum.cit.aet.artemis.iris.service.IrisRateLimitService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisConnectorException;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisHealthIndicator;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisWebhookService;

@Profile(PROFILE_IRIS)
@RestController
@RequestMapping("api/iris/transcription-ingestion")
public class IrisTranscriptionResource {

    private static final Logger log = LoggerFactory.getLogger(IrisTranscriptionResource.class);

    protected final UserRepository userRepository;

    protected final IrisRateLimitService irisRateLimitService;

    protected final PyrisHealthIndicator pyrisHealthIndicator;

    private final AuthorizationCheckService authorizationCheckService;

    private final CourseRepository courseRepository;

    private final PyrisWebhookService pyrisWebhookService;

    public IrisTranscriptionResource(UserRepository userRepository, PyrisHealthIndicator pyrisHealthIndicator, IrisRateLimitService irisRateLimitService,
            AuthorizationCheckService authorizationCheckService, CourseRepository courseRepository, PyrisWebhookService pyrisWebhookService) {
        this.userRepository = userRepository;
        this.pyrisHealthIndicator = pyrisHealthIndicator;
        this.irisRateLimitService = irisRateLimitService;
        this.authorizationCheckService = authorizationCheckService;
        this.courseRepository = courseRepository;
        this.pyrisWebhookService = pyrisWebhookService;
    }

    /**
     * Retrieves the overall ingestion state of a Transcription by communicating with Pyris.
     *
     * <p>
     * This method sends a GET request to the external Pyris service to fetch the current ingestion
     * state of all Transcription in a course, identified by its `lectureId`. The ingestion state can be aggregated from
     * multiple lecture units or can reflect the overall status of the lecture ingestion process.
     * </p>
     *
     * @param courseId the ID of the lecture for which the ingestion state is being requested
     * @return a {@link ResponseEntity} containing the {@link IngestionState} of the lecture,
     */
    @GetMapping("courses/{courseId}/lectures/transcription-ingestion-state")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<Map<Long, IngestionState>> getStatusOfTranscriptionIngestion(@PathVariable long courseId) {
        try {
            Course course = courseRepository.findByIdElseThrow(courseId);
            authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
            return ResponseEntity.ok(pyrisWebhookService.getLecturesIngestionState(courseId)); // update to new webhook service for transcription
        }
        catch (PyrisConnectorException e) {
            log.error("Error fetching ingestion state for course {}", courseId, e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }

    /**
     * Retrieves the ingestion state of all transcription unit in a lecture by communicating with Pyris.
     *
     * <p>
     * This method sends a GET request to the external Pyris service to fetch the current ingestion
     * state of a lecture unit, identified by its ID. It constructs a request using the provided
     * `lectureId` and `lectureUnitId` and returns the state of the ingestion process (e.g., NOT_STARTED,
     * IN_PROGRESS, DONE, ERROR).
     * </p>
     *
     * @param courseId  the ID of the lecture the unit belongs to
     * @param lectureId the ID of the lecture the unit belongs to
     * @return a {@link ResponseEntity} containing the {@link IngestionState} of the lecture unit,
     */
    @GetMapping("courses/{courseId}/lectures/{lectureId}/lecture-units/transcription-ingestion-state")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<Map<Long, IngestionState>> getStatusOfLectureUnitsTranscriptionIngestion(@PathVariable long courseId, @PathVariable long lectureId) {
        try {
            Course course = courseRepository.findByIdElseThrow(courseId);
            authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
            return ResponseEntity.ok(pyrisWebhookService.getLectureUnitsIngestionState(courseId, lectureId)); // update to new webhook service for transcription
        }
        catch (PyrisConnectorException e) {
            log.error("Error fetching ingestion state for lecture {}", lectureId, e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
    }
}
