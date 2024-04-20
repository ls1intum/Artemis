package de.tum.in.www1.artemis.web.rest.tutorialgroups;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;
import static de.tum.in.www1.artemis.web.rest.util.DateUtil.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

import jakarta.validation.Valid;
import jakarta.ws.rs.BadRequestException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupFreePeriod;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupsConfiguration;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupFreePeriodRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupsConfigurationRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastInstructor;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggle;
import de.tum.in.www1.artemis.service.tutorialgroups.TutorialGroupFreePeriodService;
import de.tum.in.www1.artemis.web.rest.dto.TutorialGroupFreePeriodDTO;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class TutorialGroupFreePeriodResource {

    private static final String ENTITY_NAME = "tutorialGroupFreePeriod";

    private static final Logger log = LoggerFactory.getLogger(TutorialGroupFreePeriodResource.class);

    private final TutorialGroupsConfigurationRepository tutorialGroupsConfigurationRepository;

    private final TutorialGroupFreePeriodRepository tutorialGroupFreePeriodRepository;

    private final TutorialGroupFreePeriodService tutorialGroupFreePeriodService;

    private final AuthorizationCheckService authorizationCheckService;

    public TutorialGroupFreePeriodResource(TutorialGroupsConfigurationRepository tutorialGroupsConfigurationRepository,
            TutorialGroupFreePeriodRepository tutorialGroupFreePeriodRepository, TutorialGroupFreePeriodService tutorialGroupFreePeriodService,
            AuthorizationCheckService authorizationCheckService) {
        this.tutorialGroupsConfigurationRepository = tutorialGroupsConfigurationRepository;
        this.tutorialGroupFreePeriodRepository = tutorialGroupFreePeriodRepository;
        this.tutorialGroupFreePeriodService = tutorialGroupFreePeriodService;
        this.authorizationCheckService = authorizationCheckService;
    }

    /**
     * GET /courses/:courseId/tutorial-groups-configuration/:tutorialGroupsConfigurationId/tutorial-free-periods/:tutorialGroupFreePeriodId : gets the tutorial group free period
     * with the specified id.
     *
     * @param courseId                      the id of the course to which the tutorial groups configuration belongs
     * @param tutorialGroupsConfigurationId the id of the tutorial groups configuration to which the tutorial group free period belongs
     * @param tutorialGroupFreePeriodId     the id of the tutorial group free period to get
     * @return ResponseEntity with status 200 (OK) and with body the tutorial group free period
     */
    @GetMapping("courses/{courseId}/tutorial-groups-configuration/{tutorialGroupsConfigurationId}/tutorial-free-periods/{tutorialGroupFreePeriodId}")
    @EnforceAtLeastInstructor
    @FeatureToggle(Feature.TutorialGroups)
    public ResponseEntity<TutorialGroupFreePeriod> getOneOfConfiguration(@PathVariable Long courseId, @PathVariable Long tutorialGroupsConfigurationId,
            @PathVariable Long tutorialGroupFreePeriodId) {
        log.debug("REST request to get tutorial group free period: {} of tutorial group configuration {} of course: {}", tutorialGroupFreePeriodId, tutorialGroupsConfigurationId,
                courseId);
        var freePeriod = tutorialGroupFreePeriodRepository.findByIdElseThrow(tutorialGroupFreePeriodId);
        checkEntityIdMatchesPathIds(freePeriod, Optional.ofNullable(courseId), Optional.ofNullable(tutorialGroupsConfigurationId));
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, freePeriod.getTutorialGroupsConfiguration().getCourse(), null);
        return ResponseEntity.ok(freePeriod);
    }

    /**
     * PUT courses/:courseId/tutorial-groups-configuration/:tutorialGroupsConfigurationId/tutorial-free-periods/:tutorialGroupFreePeriodId : Updates an existing tutorial free
     * period
     *
     * @param courseId                      the id of the course to which the tutorial groups configuration belongs
     * @param tutorialGroupsConfigurationId the id of the tutorial groups configuration to which the tutorial group free period should be added
     * @param tutorialGroupFreePeriodId     the id of the tutorial group free period to update
     * @param tutorialGroupFreePeriod       tutorial group free period that should be created
     * @return ResponseEntity with status 201 (Created) and in the body the new tutorial group free period
     */
    @PutMapping("courses/{courseId}/tutorial-groups-configuration/{tutorialGroupsConfigurationId}/tutorial-free-periods/{tutorialGroupFreePeriodId}")
    @EnforceAtLeastInstructor
    @FeatureToggle(Feature.TutorialGroups)
    public ResponseEntity<TutorialGroupFreePeriod> update(@PathVariable Long courseId, @PathVariable Long tutorialGroupsConfigurationId,
            @PathVariable Long tutorialGroupFreePeriodId, @RequestBody @Valid TutorialGroupFreePeriodDTO tutorialGroupFreePeriod) throws URISyntaxException {
        log.debug("REST request to update TutorialGroupFreePeriod: {} for tutorial group configuration: {} of course: {}", tutorialGroupFreePeriodId, tutorialGroupsConfigurationId,
                courseId);
        if (tutorialGroupFreePeriod.endDate().isBefore(tutorialGroupFreePeriod.startDate())) {
            throw new BadRequestException("The start date must be before the end date");
        }
        var existingFreePeriod = tutorialGroupFreePeriodRepository.findByIdElseThrow(tutorialGroupFreePeriodId);
        checkEntityIdMatchesPathIds(existingFreePeriod, Optional.ofNullable(courseId), Optional.ofNullable(tutorialGroupsConfigurationId));
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, existingFreePeriod.getTutorialGroupsConfiguration().getCourse(), null);

        Optional<TutorialGroupsConfiguration> configurationOptional = tutorialGroupsConfigurationRepository.findByCourseIdWithEagerTutorialGroupFreePeriods(courseId);
        var configuration = configurationOptional.orElseThrow(() -> new BadRequestException("The course has no tutorial groups configuration"));
        if (configuration.getCourse().getTimeZone() == null) {
            throw new BadRequestException("The course has no time zone");
        }

        TutorialGroupFreePeriod updatedFreePeriod = new TutorialGroupFreePeriod();
        updatedFreePeriod.setId(existingFreePeriod.getId());
        updatedFreePeriod.setTutorialGroupsConfiguration(configuration);
        updatedFreePeriod.setReason(tutorialGroupFreePeriod.reason());
        updatedFreePeriod.setStart(
                interpretInTimeZone(tutorialGroupFreePeriod.startDate().toLocalDate(), tutorialGroupFreePeriod.startDate().toLocalTime(), configuration.getCourse().getTimeZone()));
        updatedFreePeriod.setEnd(
                interpretInTimeZone(tutorialGroupFreePeriod.endDate().toLocalDate(), tutorialGroupFreePeriod.endDate().toLocalTime(), configuration.getCourse().getTimeZone()));
        isValidTutorialGroupPeriod(updatedFreePeriod);

        // activate previously cancelled sessions
        tutorialGroupFreePeriodService.updateOverlappingSessions(configuration.getCourse(), existingFreePeriod, updatedFreePeriod, false);
        // update free period
        updatedFreePeriod = tutorialGroupFreePeriodRepository.save(updatedFreePeriod);
        // cancel now overlapping sessions
        tutorialGroupFreePeriodService.cancelOverlappingSessions(configuration.getCourse(), updatedFreePeriod);

        return ResponseEntity.ok(updatedFreePeriod);
    }

    /**
     * POST courses/:courseId/tutorial-groups-configuration/:tutorialGroupsConfigurationId/tutorial-free-periods : creates a new tutorial group free period
     *
     * @param courseId                      the id of the course to which the tutorial groups configuration belongs
     * @param tutorialGroupsConfigurationId the id of the tutorial groups configuration to which the tutorial group free period should be added
     * @param tutorialGroupFreePeriod       tutorial group free period that should be created
     * @return ResponseEntity with status 201 (Created) and in the body the new tutorial group free period
     */
    @PostMapping("courses/{courseId}/tutorial-groups-configuration/{tutorialGroupsConfigurationId}/tutorial-free-periods")
    @EnforceAtLeastInstructor
    @FeatureToggle(Feature.TutorialGroups)
    public ResponseEntity<TutorialGroupFreePeriod> create(@PathVariable Long courseId, @PathVariable Long tutorialGroupsConfigurationId,
            @RequestBody @Valid TutorialGroupFreePeriodDTO tutorialGroupFreePeriod) throws URISyntaxException {
        log.debug("REST request to create TutorialGroupFreePeriod: {} for tutorial group configuration: {} of course: {}", tutorialGroupFreePeriod, tutorialGroupsConfigurationId,
                courseId);
        if (tutorialGroupFreePeriod.endDate().isBefore(tutorialGroupFreePeriod.startDate())) {
            throw new BadRequestException("The start date must be before the end date");
        }
        TutorialGroupsConfiguration tutorialGroupsConfiguration = tutorialGroupsConfigurationRepository
                .findByIdWithEagerTutorialGroupFreePeriodsElseThrow(tutorialGroupsConfigurationId);
        if (tutorialGroupsConfiguration.getCourse().getTimeZone() == null) {
            throw new BadRequestException("The course has no time zone");
        }
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, tutorialGroupsConfiguration.getCourse(), null);

        TutorialGroupFreePeriod newTutorialGroupFreePeriod = new TutorialGroupFreePeriod();
        newTutorialGroupFreePeriod.setTutorialGroupsConfiguration(tutorialGroupsConfiguration);
        newTutorialGroupFreePeriod.setReason(tutorialGroupFreePeriod.reason());

        newTutorialGroupFreePeriod.setStart(interpretInTimeZone(tutorialGroupFreePeriod.startDate().toLocalDate(), tutorialGroupFreePeriod.startDate().toLocalTime(),
                tutorialGroupsConfiguration.getCourse().getTimeZone()));
        newTutorialGroupFreePeriod.setEnd(interpretInTimeZone(tutorialGroupFreePeriod.endDate().toLocalDate(), tutorialGroupFreePeriod.endDate().toLocalTime(),
                tutorialGroupsConfiguration.getCourse().getTimeZone()));

        checkEntityIdMatchesPathIds(newTutorialGroupFreePeriod, Optional.ofNullable(courseId), Optional.ofNullable(tutorialGroupsConfigurationId));
        isValidTutorialGroupPeriod(newTutorialGroupFreePeriod);
        trimStringFields(newTutorialGroupFreePeriod);
        var persistedTutorialGroupFreePeriod = tutorialGroupFreePeriodRepository.save(newTutorialGroupFreePeriod);

        tutorialGroupFreePeriodService.cancelOverlappingSessions(tutorialGroupsConfiguration.getCourse(), persistedTutorialGroupFreePeriod);

        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/tutorial-groups-configuration/" + tutorialGroupsConfigurationId + "/tutorial-free-periods/"
                + persistedTutorialGroupFreePeriod.getId())).body(persistedTutorialGroupFreePeriod);
    }

    /**
     * DELETE courses/:courseId/tutorial-groups-configuration/:tutorialGroupsConfigurationId/tutorial-free-periods/tutorial-free-periods : deletes a tutorial free period
     *
     * @param courseId                      the id of the course to which the tutorial groups configuration belongs
     * @param tutorialGroupsConfigurationId the id of the tutorial groups configuration to which the tutorial group free period should be added
     * @param tutorialGroupFreePeriodId     the id of the tutorial group free period that should be deleted
     * @return ResponseEntity with the status 204 (No Content)
     */
    @DeleteMapping("courses/{courseId}/tutorial-groups-configuration/{tutorialGroupsConfigurationId}/tutorial-free-periods/{tutorialGroupFreePeriodId}")
    @EnforceAtLeastInstructor
    @FeatureToggle(Feature.TutorialGroups)
    public ResponseEntity<Void> delete(@PathVariable Long courseId, @PathVariable Long tutorialGroupsConfigurationId, @PathVariable Long tutorialGroupFreePeriodId)
            throws URISyntaxException {
        log.debug("REST request to delete TutorialGroupFreePeriod: {} of tutorial group configuration {} of course: {}", tutorialGroupFreePeriodId, tutorialGroupsConfigurationId,
                courseId);
        TutorialGroupFreePeriod tutorialGroupFreePeriod = tutorialGroupFreePeriodRepository.findByIdElseThrow(tutorialGroupFreePeriodId);
        checkEntityIdMatchesPathIds(tutorialGroupFreePeriod, Optional.ofNullable(courseId), Optional.ofNullable(tutorialGroupsConfigurationId));
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, tutorialGroupFreePeriod.getTutorialGroupsConfiguration().getCourse(), null);
        Optional<TutorialGroupsConfiguration> configurationOptional = tutorialGroupsConfigurationRepository.findByCourseIdWithEagerTutorialGroupFreePeriods(courseId);
        TutorialGroupsConfiguration configuration = configurationOptional
                .orElseThrow(() -> new BadRequestException("The course has no tutorial groups configuration with ID " + tutorialGroupsConfigurationId));
        tutorialGroupFreePeriodService.updateOverlappingSessions(configuration.getCourse(), tutorialGroupFreePeriod, null, true);
        tutorialGroupFreePeriodRepository.delete(tutorialGroupFreePeriod);
        return ResponseEntity.noContent().build();
    }

    private void checkEntityIdMatchesPathIds(TutorialGroupFreePeriod tutorialGroupFreePeriod, Optional<Long> courseId, Optional<Long> tutorialGroupsConfigurationId) {
        courseId.ifPresent(courseIdValue -> {
            if (!tutorialGroupFreePeriod.getTutorialGroupsConfiguration().getCourse().getId().equals(courseIdValue)) {
                throw new BadRequestAlertException("The courseId in the path does not match the courseId in the tutorial groups configuration", ENTITY_NAME, "courseIdMismatch");
            }
        });
        tutorialGroupsConfigurationId.ifPresent(configurationIdValue -> {
            if (!tutorialGroupFreePeriod.getTutorialGroupsConfiguration().getId().equals(configurationIdValue)) {
                throw new BadRequestAlertException("The tutorialGroupConfigurationId in the path does not match the id in the tutorial group configuration", ENTITY_NAME,
                        "tutorialGroupConfigurationIdMismatch");
            }
        });
    }

    private void isValidTutorialGroupPeriod(TutorialGroupFreePeriod tutorialGroupFreePeriod) {
        if (tutorialGroupFreePeriod.getStart() == null || tutorialGroupFreePeriod.getEnd() == null) {
            throw new BadRequestAlertException("The start or end date of the tutorial group free period is null", ENTITY_NAME, "nullDate");
        }
        if (tutorialGroupFreePeriod.getStart().isAfter(tutorialGroupFreePeriod.getEnd())) {
            throw new BadRequestAlertException("The start date must be before the end date", ENTITY_NAME, "invalidDateRange");
        }
        this.checkForOverlapWithPeriod(tutorialGroupFreePeriod);
    }

    /**
     * This method checks if the given tutorial group free period overlaps with any other tutorial group free period in the same course.
     * If there is an overlap, it throws a BadRequestAlertException.
     *
     * @param tutorialGroupFreePeriod the tutorial group free period to check for overlaps. It should have a valid start and end date, and belong to a course.
     * @throws BadRequestAlertException if the given tutorial group free period overlaps with another tutorial group free period in the same course.
     */
    private void checkForOverlapWithPeriod(TutorialGroupFreePeriod tutorialGroupFreePeriod) {
        var overlappingPeriod = tutorialGroupFreePeriodRepository.findOverlappingInSameCourseExclusive(tutorialGroupFreePeriod.getTutorialGroupsConfiguration().getCourse(),
                tutorialGroupFreePeriod.getStart(), tutorialGroupFreePeriod.getEnd());
        var overlappingPeriodOptional = overlappingPeriod.stream().filter(period -> !period.getId().equals(tutorialGroupFreePeriod.getId())).findFirst();
        if (overlappingPeriodOptional.isPresent()) {
            throw new BadRequestAlertException("The given tutorial group free period overlaps with another tutorial group free period with ID "
                    + overlappingPeriodOptional.get().getId() + " in the same course.", ENTITY_NAME, "overlapping");
        }
    }

    private void trimStringFields(TutorialGroupFreePeriod tutorialGroupFreePeriod) {
        if (tutorialGroupFreePeriod.getReason() != null) {
            tutorialGroupFreePeriod.setReason(tutorialGroupFreePeriod.getReason().trim());
        }
    }
}
