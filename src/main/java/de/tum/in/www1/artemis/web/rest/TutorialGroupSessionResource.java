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
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroup;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.TutorialGroupSessionRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggle;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;

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
     * POST /courses/:courseId/tutorial-groups/:tutorialGroupId/sessions : creates a new tutorial group.
     *
     * @param courseId             the id of the course to which the tutorial group should be added
     * @param tutorialGroupSession the tutorial group that should be created
     * @return ResponseEntity with status 201 (Created) and in the body the new tutorial group
     */
    @PostMapping("/courses/{courseId}/tutorial-groups/{tutorialGroupId}/sessions")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @FeatureToggle(Feature.TutorialGroups)
    public ResponseEntity<TutorialGroupSession> create(@PathVariable Long courseId, @PathVariable Long tutorialGroupId,
            @RequestBody @Valid TutorialGroupSession tutorialGroupSession) throws URISyntaxException {
        log.debug("REST request to create TutorialGroupSession: {} in course: {}", tutorialGroupSession, courseId);
        if (tutorialGroupSession.getId() != null) {
            throw new BadRequestException("A new tutorial group session cannot already have an ID");
        }
        var tutorialGroup = tutorialGroupRepository.findByIdWithSessionsElseThrow(tutorialGroupId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, tutorialGroup.getCourse(), null);

        checkPathIdsAgainstDatabaseIds(courseId, tutorialGroup);

        if (tutorialGroup.getCourse().getTutorialGroupsConfiguration() == null) {
            throw new BadRequestException("The course has no tutorial groups configuration");
        }
        tutorialGroupSession.setTutorialGroup(tutorialGroup);
        tutorialGroupSession.setStatus(TutorialGroupSessionStatus.ACTIVE);

        tutorialGroupSessionRepository.save(tutorialGroupSession);

        return ResponseEntity.created(new URI("")).body(tutorialGroupSession);
    }

    @PostMapping("/courses/{courseId}/tutorial-groups/{tutorialGroupId}/sessions/{sessionId}/cancel")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @FeatureToggle(Feature.TutorialGroups)
    public ResponseEntity<TutorialGroupSession> create(@PathVariable Long courseId, @PathVariable Long tutorialGroupId, @PathVariable Long sessionId) throws URISyntaxException {
        log.debug("REST request to cancel TutorialGroupSession: {} in tutorialGroup: {}", sessionId, tutorialGroupId);
        var tutorialGroupSession = tutorialGroupSessionRepository.findByIdElseThrow(sessionId);
        checkPathIdsAgainstDatabaseIds(courseId, tutorialGroupId, tutorialGroupSession);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, tutorialGroupSession.getTutorialGroup().getCourse(), null);

        tutorialGroupSession.setStatus(TutorialGroupSessionStatus.CANCELLED);
        tutorialGroupSessionRepository.save(tutorialGroupSession);

        return ResponseEntity.ok().body(tutorialGroupSession);
    }

    @PostMapping("/courses/{courseId}/tutorial-groups/{tutorialGroupId}/sessions/{sessionId}/activate")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @FeatureToggle(Feature.TutorialGroups)
    public ResponseEntity<TutorialGroupSession> activate(@PathVariable Long courseId, @PathVariable Long tutorialGroupId, @PathVariable Long sessionId) throws URISyntaxException {
        log.debug("REST request to cancel TutorialGroupSession: {} in tutorialGroup: {}", sessionId, tutorialGroupId);
        var tutorialGroupSession = tutorialGroupSessionRepository.findByIdElseThrow(sessionId);
        checkPathIdsAgainstDatabaseIds(courseId, tutorialGroupId, tutorialGroupSession);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, tutorialGroupSession.getTutorialGroup().getCourse(), null);

        tutorialGroupSession.setStatus(TutorialGroupSessionStatus.ACTIVE);
        tutorialGroupSessionRepository.save(tutorialGroupSession);

        return ResponseEntity.ok().body(tutorialGroupSession);
    }

    private void checkPathIdsAgainstDatabaseIds(Long courseIdFromPath, TutorialGroup tutorialGroupFromDatabase) {
        if (!tutorialGroupFromDatabase.getCourse().getId().equals(courseIdFromPath)) {
            throw new ConflictException("The tutorial group does not belong to the correct course", "TutorialGroup", "tutorialGroupWrongCourse");
        }
    }

    private void checkPathIdsAgainstDatabaseIds(Long courseIdFromPath, Long tutorialGroupIdFromPath, TutorialGroupSession tutorialGroupSession) {
        if (!tutorialGroupSession.getTutorialGroup().getId().equals(tutorialGroupIdFromPath)) {
            throw new ConflictException("The tutorial group session does not belong to the correct tutorial group", "TutorialGroupSession",
                    "tutorialGroupSessionWrongTutorialGroup");
        }
        if (!tutorialGroupSession.getTutorialGroup().getCourse().getId().equals(courseIdFromPath)) {
            throw new ConflictException("The tutorial group does not belong to the correct course", "TutorialGroup", "tutorialGroupWrongCourse");
        }
    }

}
