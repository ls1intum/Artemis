package de.tum.in.www1.artemis.web.rest;

import java.net.URI;
import java.net.URISyntaxException;

import javax.validation.Valid;
import javax.ws.rs.BadRequestException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.TutorialGroupSession;
import de.tum.in.www1.artemis.domain.enumeration.TutorialGroupSessionStatus;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.TutorialGroupSessionRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggle;

@RestController
@RequestMapping("/api")
public class TutorialGroupSessionResource {

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private static final String ENTITY_NAME = "tutorialGroupSession";

    private final Logger log = LoggerFactory.getLogger(TutorialGroupSession.class);

    private final TutorialGroupSessionRepository tutorialGroupSessionRepository;

    private final TutorialGroupRepository tutorialGroupRepository;

    private final CourseRepository courseRepository;

    private final UserRepository userRepository;

    private final AuthorizationCheckService authorizationCheckService;

    public TutorialGroupSessionResource(TutorialGroupSessionRepository tutorialGroupSessionRepository, TutorialGroupRepository tutorialGroupRepository,
            CourseRepository courseRepository, UserRepository userRepository, AuthorizationCheckService authorizationCheckService) {
        this.tutorialGroupSessionRepository = tutorialGroupSessionRepository;
        this.tutorialGroupRepository = tutorialGroupRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.authorizationCheckService = authorizationCheckService;
    }

    /**
     * POST /tutorial-groups/:tutorialGroupId/sessions : creates a new tutorial group.
     *
     * @param tutorialGroupSession the tutorial group that should be created
     * @return ResponseEntity with status 201 (Created) and in the body the new tutorial group
     */
    @PostMapping("/tutorial-groups/{tutorialGroupId}/tutorial-group-sessions")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @FeatureToggle(Feature.TutorialGroups)
    public ResponseEntity<TutorialGroupSession> create(@PathVariable Long tutorialGroupId, @RequestBody @Valid TutorialGroupSession tutorialGroupSession)
            throws URISyntaxException {
        log.debug("REST request to create TutorialGroupSession: {} for tutorial group: {}", tutorialGroupSession);
        if (tutorialGroupSession.getId() != null) {
            throw new BadRequestException("A new tutorial group session cannot already have an ID");
        }
        var tutorialGroup = tutorialGroupRepository.findByIdWithSessionsElseThrow(tutorialGroupId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, tutorialGroup.getCourse(), null);
        if (tutorialGroup.getCourse().getTutorialGroupsConfiguration() == null) {
            throw new BadRequestException("The course has no tutorial groups configuration");
        }
        // ToDo: Check for overlapping sessions
        tutorialGroupSession.setTutorialGroup(tutorialGroup);
        tutorialGroupSession.setStatus(TutorialGroupSessionStatus.ACTIVE);

        tutorialGroupSessionRepository.save(tutorialGroupSession);

        return ResponseEntity.created(new URI("")).body(tutorialGroupSession);
    }

    @PostMapping("/tutorial-group-sessions/{tutorialGroupSessionId}/cancel")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @FeatureToggle(Feature.TutorialGroups)
    public ResponseEntity<TutorialGroupSession> cancel(@PathVariable Long tutorialGroupSessionId, @RequestBody StatusDTO statusDTO) throws URISyntaxException {

        log.debug("REST request to cancel TutorialGroupSession: {}", tutorialGroupSessionId);
        var tutorialGroupSession = tutorialGroupSessionRepository.findByIdElseThrow(tutorialGroupSessionId);

        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, tutorialGroupSession.getTutorialGroup().getCourse(), null);

        tutorialGroupSession.setStatus(TutorialGroupSessionStatus.CANCELLED);
        if (statusDTO != null && statusDTO.status_explanation() != null && statusDTO.status_explanation().trim().length() > 0) {
            tutorialGroupSession.setStatusExplanation(statusDTO.status_explanation().trim());
        }
        tutorialGroupSessionRepository.save(tutorialGroupSession);

        return ResponseEntity.ok().body(tutorialGroupSession);
    }

    @PostMapping("/tutorial-group-sessions/{tutorialGroupSessionId}/activate")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @FeatureToggle(Feature.TutorialGroups)
    public ResponseEntity<TutorialGroupSession> activate(@PathVariable Long tutorialGroupSessionId) throws URISyntaxException {
        log.debug("REST request to cancel TutorialGroupSession: {}", tutorialGroupSessionId);
        var tutorialGroupSession = tutorialGroupSessionRepository.findByIdElseThrow(tutorialGroupSessionId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, tutorialGroupSession.getTutorialGroup().getCourse(), null);

        tutorialGroupSession.setStatus(TutorialGroupSessionStatus.ACTIVE);
        tutorialGroupSession.setStatusExplanation(null);

        tutorialGroupSessionRepository.save(tutorialGroupSession);

        return ResponseEntity.ok().body(tutorialGroupSession);
    }

    public record StatusDTO(String status_explanation) {
    }

}
