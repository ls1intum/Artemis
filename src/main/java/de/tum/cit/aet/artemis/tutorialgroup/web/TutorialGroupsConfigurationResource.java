package de.tum.cit.aet.artemis.tutorialgroup.web;

import static de.tum.cit.aet.artemis.core.util.DateUtil.isIso8601DateString;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.tutorialgroup.config.TutorialGroupEnabled;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupsConfiguration;
import de.tum.cit.aet.artemis.tutorialgroup.dto.TutorialGroupConfigurationDTO;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupsConfigurationRepository;
import de.tum.cit.aet.artemis.tutorialgroup.service.TutorialGroupChannelManagementService;

@Conditional(TutorialGroupEnabled.class)
@Lazy
@RestController
@RequestMapping("api/tutorialgroup/")
public class TutorialGroupsConfigurationResource {

    private static final String ENTITY_NAME = "tutorialGroupsConfiguration";

    private static final Logger log = LoggerFactory.getLogger(TutorialGroupsConfigurationResource.class);

    private final TutorialGroupsConfigurationRepository tutorialGroupsConfigurationRepository;

    private final CourseRepository courseRepository;

    private final TutorialGroupChannelManagementService tutorialGroupChannelManagementService;

    private final AuthorizationCheckService authorizationCheckService;

    public TutorialGroupsConfigurationResource(TutorialGroupsConfigurationRepository tutorialGroupsConfigurationRepository, CourseRepository courseRepository,
            TutorialGroupChannelManagementService tutorialGroupChannelManagementService, AuthorizationCheckService authorizationCheckService) {
        this.tutorialGroupsConfigurationRepository = tutorialGroupsConfigurationRepository;
        this.courseRepository = courseRepository;
        this.tutorialGroupChannelManagementService = tutorialGroupChannelManagementService;
        this.authorizationCheckService = authorizationCheckService;
    }

    /**
     * GET /courses/:courseId/tutorial-groups-configuration/: gets the tutorial groups configuration of the course with the specified id
     *
     * @param courseId the id of the course to which the tutorial groups configuration belongs
     * @return ResponseEntity with status 200 (OK) and with body the tutorial groups configuration
     */
    @GetMapping("courses/{courseId}/tutorial-groups-configuration")
    @EnforceAtLeastStudent
    public ResponseEntity<TutorialGroupConfigurationDTO> getOneOfCourse(@PathVariable Long courseId) {
        log.debug("REST request to get tutorial groups configuration of course: {}", courseId);
        var course = courseRepository.findByIdElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, null);
        var configuration = tutorialGroupsConfigurationRepository.findByCourseIdWithEagerTutorialGroupFreePeriods(courseId).orElse(null);
        if (configuration == null) {
            return ResponseEntity.ok().body(null);
        }
        return ResponseEntity.ok().body(TutorialGroupConfigurationDTO.of(configuration));
    }

    /**
     * POST /courses/:courseId/tutorial-groups-configuration: creates a new tutorial group configuration for the specified course and sets the timeZone on the course.
     *
     * @param courseId                      the id of the course to which the tutorial group configuration should be added
     * @param tutorialGroupConfigurationDto the tutorial group configuration to create
     * @return ResponseEntity with status 201 (Created) and in the body the new tutorial group configuration
     */
    @PostMapping("courses/{courseId}/tutorial-groups-configuration")
    @EnforceAtLeastInstructor
    public ResponseEntity<TutorialGroupConfigurationDTO> create(@PathVariable Long courseId, @RequestBody @Valid TutorialGroupConfigurationDTO tutorialGroupConfigurationDto)
            throws URISyntaxException {
        log.debug("REST request to create TutorialGroupsConfiguration: {} for course: {}", tutorialGroupConfigurationDto, courseId);
        if (tutorialGroupConfigurationDto.id() != null) {
            throw new BadRequestAlertException("A new tutorial group configuration cannot already have an ID", ENTITY_NAME, "idExists");
        }
        var course = courseRepository.findByIdElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        if (tutorialGroupsConfigurationRepository.findByCourseIdWithEagerTutorialGroupFreePeriods(course.getId()).isPresent()) {
            throw new BadRequestAlertException("A tutorial group configuration already exists for this course", ENTITY_NAME, "alreadyExists");
        }
        if (course.getTimeZone() == null) {
            throw new BadRequestAlertException("The course has no configured time zone.", ENTITY_NAME, "courseHasNoTimeZone");
        }
        TutorialGroupsConfiguration configuration = TutorialGroupConfigurationDTO.from(tutorialGroupConfigurationDto);

        validateTutorialGroupConfiguration(configuration);
        configuration.setCourse(course);
        var persistedConfiguration = tutorialGroupsConfigurationRepository.save(configuration);
        course.setTutorialGroupsConfiguration(persistedConfiguration);
        courseRepository.save(course);

        if (persistedConfiguration.getUseTutorialGroupChannels()) {
            tutorialGroupChannelManagementService.createTutorialGroupsChannelsForAllTutorialGroupsOfCourse(course);
        }

        return ResponseEntity.created(new URI("/api/tutorialgroup/courses/" + courseId + "/tutorial-groups-configuration/" + persistedConfiguration.getId()))
                .body(TutorialGroupConfigurationDTO.of(persistedConfiguration));
    }

    /**
     * PUT /courses/:courseId/tutorial-groups-configurations/:tutorialGroupsConfigurationId : Update tutorial groups configuration.
     *
     * @param courseId                             the id of the course to which the tutorial groups configuration belongs
     * @param tutorialGroupsConfigurationId        the id of the tutorial groups configuration to update
     * @param updatedTutorialGroupConfigurationDto the configuration dto to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated tutorial group configuration dto
     */
    @PutMapping("courses/{courseId}/tutorial-groups-configuration/{tutorialGroupsConfigurationId}")
    @EnforceAtLeastInstructor
    public ResponseEntity<TutorialGroupConfigurationDTO> update(@PathVariable Long courseId, @PathVariable Long tutorialGroupsConfigurationId,
            @RequestBody @Valid TutorialGroupConfigurationDTO updatedTutorialGroupConfigurationDto) {
        log.debug("REST request to update TutorialGroupsConfiguration: {} of course: {}", updatedTutorialGroupConfigurationDto, courseId);
        if (updatedTutorialGroupConfigurationDto.id() == null) {
            throw new BadRequestAlertException("A tutorial group cannot be updated without an id", ENTITY_NAME, "idNull");
        }

        var configurationFromDatabase = this.tutorialGroupsConfigurationRepository.findByIdWithEagerTutorialGroupFreePeriodsElseThrow(updatedTutorialGroupConfigurationDto.id());
        var course = configurationFromDatabase.getCourse();
        if (course.getTimeZone() == null) {
            throw new BadRequestAlertException("The course has no configured time zone.", ENTITY_NAME, "courseHasNoTimeZone");
        }

        checkEntityIdMatchesPathIds(configurationFromDatabase, Optional.ofNullable(courseId), Optional.ofNullable(tutorialGroupsConfigurationId));
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, configurationFromDatabase.getCourse(), null);

        TutorialGroupsConfiguration updatedTutorialGroupConfiguration = TutorialGroupConfigurationDTO.from(updatedTutorialGroupConfigurationDto);

        validateTutorialGroupConfiguration(updatedTutorialGroupConfiguration);

        boolean useTutorialGroupChannelSettingChanged = !Objects.equals(configurationFromDatabase.getUseTutorialGroupChannels(),
                updatedTutorialGroupConfiguration.getUseTutorialGroupChannels());
        boolean usePublicChannelSettingChanged = !Objects.equals(configurationFromDatabase.getUsePublicTutorialGroupChannels(),
                updatedTutorialGroupConfiguration.getUsePublicTutorialGroupChannels());

        configurationFromDatabase.setTutorialPeriodEndInclusive(updatedTutorialGroupConfiguration.getTutorialPeriodEndInclusive());
        configurationFromDatabase.setTutorialPeriodStartInclusive(updatedTutorialGroupConfiguration.getTutorialPeriodStartInclusive());
        configurationFromDatabase.setUseTutorialGroupChannels(updatedTutorialGroupConfiguration.getUseTutorialGroupChannels());
        configurationFromDatabase.setUsePublicTutorialGroupChannels(updatedTutorialGroupConfiguration.getUsePublicTutorialGroupChannels());

        var persistedConfiguration = tutorialGroupsConfigurationRepository.save(configurationFromDatabase);

        if (useTutorialGroupChannelSettingChanged) {
            log.debug("Tutorial group channel setting changed, updating tutorial group channels for course: {}", persistedConfiguration.getCourse().getId());
            if (persistedConfiguration.getUseTutorialGroupChannels()) {
                tutorialGroupChannelManagementService.createTutorialGroupsChannelsForAllTutorialGroupsOfCourse(persistedConfiguration.getCourse());
            }
            else {
                tutorialGroupChannelManagementService.removeTutorialGroupChannelsForCourse(persistedConfiguration.getCourse());
            }
        }
        if (usePublicChannelSettingChanged) {
            log.debug("Tutorial group channel public setting changed, updating tutorial group channels for course: {}", persistedConfiguration.getCourse().getId());
            if (persistedConfiguration.getUseTutorialGroupChannels()) {
                tutorialGroupChannelManagementService.changeChannelModeForCourse(persistedConfiguration.getCourse(), persistedConfiguration.getUsePublicTutorialGroupChannels());
            }
        }
        return ResponseEntity.ok(TutorialGroupConfigurationDTO.of(persistedConfiguration));
    }

    private static void validateTutorialGroupConfiguration(TutorialGroupsConfiguration tutorialGroupsConfiguration) {
        if (tutorialGroupsConfiguration.getTutorialPeriodStartInclusive() == null || tutorialGroupsConfiguration.getTutorialPeriodEndInclusive() == null) {
            throw new BadRequestAlertException("Tutorial period start date and end date must be set.", ENTITY_NAME, "tutorialPeriodMissing");
        }
        if (!isIso8601DateString(tutorialGroupsConfiguration.getTutorialPeriodStartInclusive())
                || !isIso8601DateString(tutorialGroupsConfiguration.getTutorialPeriodEndInclusive())) {
            throw new BadRequestAlertException("Tutorial period start date and end date must be valid ISO 8601 date strings.", ENTITY_NAME, "tutorialPeriodInvalidFormat");
        }
        if (LocalDate.parse(tutorialGroupsConfiguration.getTutorialPeriodStartInclusive()).isAfter(LocalDate.parse(tutorialGroupsConfiguration.getTutorialPeriodEndInclusive()))) {
            throw new BadRequestAlertException("Tutorial period start date must be before tutorial period end date.", ENTITY_NAME, "tutorialPeriodInvalidOrder");
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
