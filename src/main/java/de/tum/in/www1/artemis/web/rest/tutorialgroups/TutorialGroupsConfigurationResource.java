package de.tum.in.www1.artemis.web.rest.tutorialgroups;

import static de.tum.in.www1.artemis.web.rest.tutorialgroups.TutorialGroupDateUtil.isIso8601DateString;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupsConfiguration;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupsConfigurationRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggle;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import jakarta.validation.Valid;

@RestController
public class TutorialGroupsConfigurationResource {

    private static final String ENTITY_NAME = "tutorialGroupsConfiguration";

    private final Logger log = LoggerFactory.getLogger(TutorialGroupsConfigurationResource.class);

    private final TutorialGroupsConfigurationRepository tutorialGroupsConfigurationRepository;

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authorizationCheckService;

    public TutorialGroupsConfigurationResource(TutorialGroupsConfigurationRepository tutorialGroupsConfigurationRepository, CourseRepository courseRepository,
            AuthorizationCheckService authorizationCheckService) {
        this.tutorialGroupsConfigurationRepository = tutorialGroupsConfigurationRepository;
        this.courseRepository = courseRepository;
        this.authorizationCheckService = authorizationCheckService;
    }

    /**
     * GET /courses/:courseId/tutorial-groups-configuration/: gets the tutorial groups configuration of the course with the specified id
     *
     * @param courseId the id of the course to which the tutorial groups configuration belongs
     * @return ResponseEntity with status 200 (OK) and with body the tutorial groups configuration
     */
    @GetMapping("/courses/{courseId}/tutorial-groups-configuration")
    @PreAuthorize("hasRole('USER')")
    @FeatureToggle(Feature.TutorialGroups)
    public ResponseEntity<TutorialGroupsConfiguration> getOneOfCourse(@PathVariable Long courseId) {
        log.debug("REST request to get tutorial groups configuration of course: {}", courseId);
        var course = courseRepository.findByIdElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, null);
        return ResponseEntity.ok().body(tutorialGroupsConfigurationRepository.findByCourseIdWithEagerTutorialGroupFreePeriods(courseId).orElse(null));
    }

    /**
     * POST /courses/:courseId/tutorial-groups-configuration : creates a new tutorial group configuration for the specified course and sets the timeZone on the course.
     *
     * @param courseId                    the id of the course to which the tutorial group configuration should be added
     * @param tutorialGroupsConfiguration the tutorial group configuration to create
     * @return ResponseEntity with status 201 (Created) and in the body the new tutorial group configuration
     */
    @PostMapping("/courses/{courseId}/tutorial-groups-configuration")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @FeatureToggle(Feature.TutorialGroups)
    public ResponseEntity<TutorialGroupsConfiguration> create(@PathVariable Long courseId, @RequestBody @Valid TutorialGroupsConfiguration tutorialGroupsConfiguration)
            throws URISyntaxException {
        log.debug("REST request to create TutorialGroupsConfiguration: {} for course: {}", tutorialGroupsConfiguration, courseId);
        if (tutorialGroupsConfiguration.getId() != null) {
            throw new BadRequestAlertException("A new tutorial group configuration cannot already have an ID", ENTITY_NAME, "idMustBeNull");
        }
        var course = courseRepository.findByIdElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        if (tutorialGroupsConfigurationRepository.findByCourseIdWithEagerTutorialGroupFreePeriods(course.getId()).isPresent()) {
            throw new BadRequestAlertException("A tutorial group configuration already exists for this course", ENTITY_NAME, "configurationAlreadyExists");
        }
        isValidTutorialGroupConfiguration(tutorialGroupsConfiguration);
        tutorialGroupsConfiguration.setCourse(course);
        var persistedConfiguration = tutorialGroupsConfigurationRepository.save(tutorialGroupsConfiguration);
        course.setTutorialGroupsConfiguration(persistedConfiguration);
        courseRepository.save(course);
        return ResponseEntity.created(new URI("/api/courses/" + courseId + "tutorial-groups-configuration/" + tutorialGroupsConfiguration.getId())).body(persistedConfiguration);
    }

    /**
     * PUT /courses/:courseId/tutorial-groups-configurations/:tutorialGroupsConfigurationId : Update tutorial groups configuration.
     *
     * @param courseId                          the id of the course to which the tutorial groups configuration belongs
     * @param tutorialGroupsConfigurationId     the id of the tutorial groups configuration to update
     * @param updatedTutorialGroupConfiguration the configuration to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated tutorial group configuration
     */
    @PutMapping("/courses/{courseId}/tutorial-groups-configuration/{tutorialGroupsConfigurationId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @FeatureToggle(Feature.TutorialGroups)
    public ResponseEntity<TutorialGroupsConfiguration> update(@PathVariable Long courseId, @PathVariable Long tutorialGroupsConfigurationId,
            @RequestBody @Valid TutorialGroupsConfiguration updatedTutorialGroupConfiguration) {
        log.debug("REST request to update TutorialGroupsConfiguration: {} of course: {}", updatedTutorialGroupConfiguration, courseId);
        if (updatedTutorialGroupConfiguration.getId() == null) {
            throw new BadRequestAlertException("A tutorial group cannot be updated without an id", ENTITY_NAME, "idCannotBeNull");
        }
        isValidTutorialGroupConfiguration(updatedTutorialGroupConfiguration);
        var configurationFromDatabase = this.tutorialGroupsConfigurationRepository.findByIdWithEagerTutorialGroupFreePeriodsElseThrow(updatedTutorialGroupConfiguration.getId());
        if (configurationFromDatabase.getCourse().getTimeZone() == null) {
            throw new BadRequestAlertException("The course has no time zone", ENTITY_NAME, "timeZoneMissing");
        }
        checkEntityIdMatchesPathIds(configurationFromDatabase, Optional.ofNullable(courseId), Optional.ofNullable(tutorialGroupsConfigurationId));
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, configurationFromDatabase.getCourse(), null);

        configurationFromDatabase.setTutorialPeriodEndInclusive(updatedTutorialGroupConfiguration.getTutorialPeriodEndInclusive());
        configurationFromDatabase.setTutorialPeriodStartInclusive(updatedTutorialGroupConfiguration.getTutorialPeriodStartInclusive());

        var persistedConfiguration = tutorialGroupsConfigurationRepository.save(configurationFromDatabase);
        return ResponseEntity.ok(persistedConfiguration);
    }

    private static void isValidTutorialGroupConfiguration(TutorialGroupsConfiguration tutorialGroupsConfiguration) {
        if (tutorialGroupsConfiguration.getTutorialPeriodStartInclusive() == null || tutorialGroupsConfiguration.getTutorialPeriodEndInclusive() == null) {
            throw new BadRequestAlertException("Tutorial period start and end must be set", ENTITY_NAME, "illegalConfiguration");
        }
        if (!isIso8601DateString(tutorialGroupsConfiguration.getTutorialPeriodStartInclusive())
                || !isIso8601DateString(tutorialGroupsConfiguration.getTutorialPeriodEndInclusive())) {
            throw new BadRequestAlertException("Tutorial period start and end must be valid ISO 8601 date strings", ENTITY_NAME, "invalidDate");
        }
        if (LocalDate.parse(tutorialGroupsConfiguration.getTutorialPeriodStartInclusive()).isAfter(LocalDate.parse(tutorialGroupsConfiguration.getTutorialPeriodEndInclusive()))) {
            throw new BadRequestAlertException("Tutorial period start must be before tutorial period end", ENTITY_NAME, "illegalConfiguration");
        }
    }

    private void checkEntityIdMatchesPathIds(TutorialGroupsConfiguration tutorialGroupsConfiguration, Optional<Long> courseId, Optional<Long> tutorialGroupConfigurationId) {
        courseId.ifPresent(courseIdValue -> {
            if (!tutorialGroupsConfiguration.getCourse().getId().equals(courseIdValue)) {
                throw new BadRequestAlertException("The courseId in the path does not match the courseId in the tutorial groups configuration", ENTITY_NAME, "courseIdMismatch");
            }
        });
        tutorialGroupConfigurationId.ifPresent(tutorialGroupIdValue -> {
            if (!tutorialGroupsConfiguration.getId().equals(tutorialGroupIdValue)) {
                throw new BadRequestAlertException("The tutorialGroupConfigurationId in the path does not match the id in the tutorial group configuration", ENTITY_NAME,
                        "tutorialGroupConfigurationIdMismatch");
            }
        });
    }

}
