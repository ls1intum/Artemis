package de.tum.in.www1.artemis.web.rest.tutorialgroups;

import static de.tum.in.www1.artemis.web.rest.tutorialgroups.TutorialGroupDateUtil.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.*;
import java.util.Optional;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BadRequestException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupFreePeriod;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupsConfiguration;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupFreePeriodRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupsConfigurationRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggle;
import de.tum.in.www1.artemis.service.tutorialgroups.TutorialGroupFreePeriodService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

@RestController
@RequestMapping("/api")
public class TutorialGroupFreePeriodResource {

    private static final String ENTITY_NAME = "tutorialGroupFreePeriod";

    private final Logger log = LoggerFactory.getLogger(TutorialGroupsConfigurationResource.class);

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
     * GET /courses/:courseId/tutorial-groups-configuration/:tutorialGroupsConfigurationId/tutorial-free-periods/:tutorialGroupFreePeriodId : gets the tutorial group free period with the specified id.
     *
     * @param courseId                      the id of the course to which the tutorial groups configuration belongs
     * @param tutorialGroupsConfigurationId the id of the tutorial groups configuration to which the tutorial group free period belongs
     * @param tutorialGroupFreePeriodId     the id of the tutorial group free period to get
     * @return ResponseEntity with status 200 (OK) and with body the tutorial group free period
     */
    @GetMapping("/courses/{courseId}/tutorial-groups-configuration/{tutorialGroupsConfigurationId}/tutorial-free-periods/{tutorialGroupFreePeriodId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
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
     * PUT courses/:courseId/tutorial-groups-configuration/:tutorialGroupsConfigurationId/tutorial-free-periods/:tutorialGroupFreePeriodId : Updates an existing tutorial free period
     *
     * @param courseId                      the id of the course to which the tutorial groups configuration belongs
     * @param tutorialGroupsConfigurationId the id of the tutorial groups configuration to which the tutorial group free period should be added
     * @param tutorialGroupFreePeriod       tutorial group free period that should be created
     * @return ResponseEntity with status 201 (Created) and in the body the new tutorial group free period
     */
    @PutMapping("/courses/{courseId}/tutorial-groups-configuration/{tutorialGroupsConfigurationId}/tutorial-free-periods/{tutorialGroupFreePeriodId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @FeatureToggle(Feature.TutorialGroups)
    public ResponseEntity<TutorialGroupFreePeriod> update(@PathVariable Long courseId, @PathVariable Long tutorialGroupsConfigurationId,
            @PathVariable Long tutorialGroupFreePeriodId, @RequestBody @Valid TutorialGroupFreePeriodDTO tutorialGroupFreePeriod) throws URISyntaxException {
        log.debug("REST request to update TutorialGroupFreePeriod: {} for tutorial group configuration: {} of course: {}", tutorialGroupFreePeriodId, tutorialGroupsConfigurationId,
                courseId);
        var existingFreePeriod = tutorialGroupFreePeriodRepository.findByIdElseThrow(tutorialGroupFreePeriodId);
        checkEntityIdMatchesPathIds(existingFreePeriod, Optional.ofNullable(courseId), Optional.ofNullable(tutorialGroupsConfigurationId));
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, existingFreePeriod.getTutorialGroupsConfiguration().getCourse(), null);

        Optional<TutorialGroupsConfiguration> configurationOptional = tutorialGroupsConfigurationRepository.findByCourse_Id(courseId);
        if (configurationOptional.isEmpty()) {
            throw new BadRequestException("The course has no tutorial groups configuration");
        }
        var configuration = configurationOptional.get();

        TutorialGroupFreePeriod updatedFreePeriod = new TutorialGroupFreePeriod();
        updatedFreePeriod.setId(existingFreePeriod.getId());
        updatedFreePeriod.setTutorialGroupsConfiguration(configuration);
        updatedFreePeriod.setReason(tutorialGroupFreePeriod.reason);
        updatedFreePeriod.setStart(interpretInTimeZoneOfConfiguration(tutorialGroupFreePeriod.date, START_OF_DAY, configuration));
        updatedFreePeriod.setEnd(interpretInTimeZoneOfConfiguration(tutorialGroupFreePeriod.date, END_OF_DAY, configuration));
        isValidTutorialGroupPeriod(updatedFreePeriod);

        // activate previously cancelled sessions
        tutorialGroupFreePeriodService.activateCancelledOverlappingSessions(configuration.getCourse(), existingFreePeriod);
        // update free period
        updatedFreePeriod = tutorialGroupFreePeriodRepository.save(updatedFreePeriod);
        // cancel now overlapping sessions
        tutorialGroupFreePeriodService.cancelActiveOverlappingSessions(configuration.getCourse(), updatedFreePeriod);

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
    @PostMapping("/courses/{courseId}/tutorial-groups-configuration/{tutorialGroupsConfigurationId}/tutorial-free-periods")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @FeatureToggle(Feature.TutorialGroups)
    public ResponseEntity<TutorialGroupFreePeriod> create(@PathVariable Long courseId, @PathVariable Long tutorialGroupsConfigurationId,
            @RequestBody @Valid TutorialGroupFreePeriodDTO tutorialGroupFreePeriod) throws URISyntaxException {
        log.debug("REST request to create TutorialGroupFreePeriod: {} for tutorial group configuration: {} of course: {}", tutorialGroupFreePeriod, tutorialGroupsConfigurationId,
                courseId);
        TutorialGroupsConfiguration tutorialGroupsConfiguration = tutorialGroupsConfigurationRepository
                .findByIdWithEagerTutorialGroupFreePeriodsElseThrow(tutorialGroupsConfigurationId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, tutorialGroupsConfiguration.getCourse(), null);

        TutorialGroupFreePeriod newTutorialGroupFreePeriod = new TutorialGroupFreePeriod();
        newTutorialGroupFreePeriod.setTutorialGroupsConfiguration(tutorialGroupsConfiguration);
        newTutorialGroupFreePeriod.setReason(tutorialGroupFreePeriod.reason);

        newTutorialGroupFreePeriod.setStart(interpretInTimeZoneOfConfiguration(tutorialGroupFreePeriod.date, START_OF_DAY, tutorialGroupsConfiguration));
        newTutorialGroupFreePeriod.setEnd(interpretInTimeZoneOfConfiguration(tutorialGroupFreePeriod.date, END_OF_DAY, tutorialGroupsConfiguration));

        checkEntityIdMatchesPathIds(newTutorialGroupFreePeriod, Optional.ofNullable(courseId), Optional.ofNullable(tutorialGroupsConfigurationId));
        isValidTutorialGroupPeriod(newTutorialGroupFreePeriod);
        trimStringFields(newTutorialGroupFreePeriod);
        var persistedTutorialGroupFreePeriod = tutorialGroupFreePeriodRepository.save(newTutorialGroupFreePeriod);

        tutorialGroupFreePeriodService.cancelActiveOverlappingSessions(tutorialGroupsConfiguration.getCourse(), persistedTutorialGroupFreePeriod);

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
    @DeleteMapping("/courses/{courseId}/tutorial-groups-configuration/{tutorialGroupsConfigurationId}/tutorial-free-periods/{tutorialGroupFreePeriodId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @FeatureToggle(Feature.TutorialGroups)
    public ResponseEntity<Void> delete(@PathVariable Long courseId, @PathVariable Long tutorialGroupsConfigurationId, @PathVariable Long tutorialGroupFreePeriodId)
            throws URISyntaxException {
        log.debug("REST request to delete TutorialGroupFreePeriod: {}", tutorialGroupFreePeriodId);
        TutorialGroupFreePeriod tutorialGroupFreePeriod = tutorialGroupFreePeriodRepository.findByIdElseThrow(tutorialGroupFreePeriodId);
        checkEntityIdMatchesPathIds(tutorialGroupFreePeriod, Optional.ofNullable(courseId), Optional.ofNullable(tutorialGroupsConfigurationId));
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, tutorialGroupFreePeriod.getTutorialGroupsConfiguration().getCourse(), null);
        tutorialGroupFreePeriodRepository.delete(tutorialGroupFreePeriod);
        tutorialGroupFreePeriodService.activateCancelledOverlappingSessions(tutorialGroupFreePeriod.getTutorialGroupsConfiguration().getCourse(), tutorialGroupFreePeriod);
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

    // ToDo: Should Actually Check DTO
    private void isValidTutorialGroupPeriod(TutorialGroupFreePeriod tutorialGroupFreePeriod) {
        if (tutorialGroupFreePeriod.getStart() == null || tutorialGroupFreePeriod.getEnd() == null) {
            throw new BadRequestAlertException("The start or end date of the tutorial group free period is null", ENTITY_NAME, "nullDate");
        }
        if (tutorialGroupFreePeriod.getStart().isAfter(tutorialGroupFreePeriod.getEnd())) {
            throw new BadRequestAlertException("The start date must be before the end date", ENTITY_NAME, "invalidDateRange");
        }
        this.checkForOverlapWithPeriod(tutorialGroupFreePeriod);
    }

    private void checkForOverlapWithPeriod(TutorialGroupFreePeriod tutorialGroupFreePeriod) {
        var overlappingPeriod = tutorialGroupFreePeriodRepository.findOverlappingInSameCourse(tutorialGroupFreePeriod.getTutorialGroupsConfiguration().getCourse(),
                tutorialGroupFreePeriod.getStart(), tutorialGroupFreePeriod.getEnd());
        if (overlappingPeriod.isPresent() && !overlappingPeriod.get().getId().equals(tutorialGroupFreePeriod.getId())) {
            throw new BadRequestAlertException("The given tutorial group free period overlaps with another tutorial group free period in the same course", ENTITY_NAME,
                    "overlapping");
        }
    }

    private void trimStringFields(TutorialGroupFreePeriod tutorialGroupFreePeriod) {
        if (tutorialGroupFreePeriod.getReason() != null) {
            tutorialGroupFreePeriod.setReason(tutorialGroupFreePeriod.getReason().trim());
        }
    }

    /**
     * Used because we want to interpret the date in the time zone of the tutorial groups configuration
     *
     * @param date
     * @param reason
     */
    public record TutorialGroupFreePeriodDTO(@NotNull LocalDate date, String reason) {
    }
}
