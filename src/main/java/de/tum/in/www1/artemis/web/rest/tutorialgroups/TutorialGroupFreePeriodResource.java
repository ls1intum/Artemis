package de.tum.in.www1.artemis.web.rest.tutorialgroups;

import static de.tum.in.www1.artemis.web.rest.tutorialgroups.TutorialGroupDateUtil.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupFreePeriod;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupsConfiguration;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupFreePeriodRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupsConfigurationRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggle;
import de.tum.in.www1.artemis.service.tutorialgroups.TutorialGroupFreePeriodService;

@RestController
@RequestMapping("/api")
public class TutorialGroupFreePeriodResource {

    private final Logger log = LoggerFactory.getLogger(TutorialGroupsConfigurationResource.class);

    private final TutorialGroupsConfigurationRepository tutorialGroupsConfigurationRepository;

    private final TutorialGroupFreePeriodRepository tutorialGroupFreePeriodRepository;

    private final TutorialGroupFreePeriodService tutorialGroupFreePeriodService;

    private final AuthorizationCheckService authorizationCheckService;

    public TutorialGroupFreePeriodResource(TutorialGroupsConfigurationRepository tutorialGroupsConfigurationRepository, CourseRepository courseRepository,
            TutorialGroupFreePeriodRepository tutorialGroupFreePeriodRepository, TutorialGroupFreePeriodService tutorialGroupFreePeriodService,
            AuthorizationCheckService authorizationCheckService) {
        this.tutorialGroupsConfigurationRepository = tutorialGroupsConfigurationRepository;
        this.tutorialGroupFreePeriodRepository = tutorialGroupFreePeriodRepository;
        this.tutorialGroupFreePeriodService = tutorialGroupFreePeriodService;
        this.authorizationCheckService = authorizationCheckService;
    }

    /**
     * POST /tutorial-groups-configurations/:tutorialGroupsConfigurationId/tutorial-free-periods : creates a new tutorial group free period
     *
     * @param tutorialGroupsConfigurationId the id of the tutorial groups configuration to which the tutorial group free period should be added
     * @param tutorialGroupFreePeriod       tutorial group free period that should be created
     * @return ResponseEntity with status 201 (Created) and in the body the new tutorial group free period
     */
    @PostMapping("/tutorial-groups-configurations/{tutorialGroupsConfigurationId}/tutorial-free-periods")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @FeatureToggle(Feature.TutorialGroups)
    public ResponseEntity<TutorialGroupFreePeriod> create(@PathVariable Long tutorialGroupsConfigurationId, @RequestBody TutorialGroupFreePeriodDTO tutorialGroupFreePeriod)
            throws URISyntaxException {
        log.debug("REST request to create TutorialGroupFreePeriod: {} for tutorial group configuration: {}", tutorialGroupFreePeriod, tutorialGroupsConfigurationId);
        TutorialGroupsConfiguration tutorialGroupsConfiguration = tutorialGroupsConfigurationRepository.findByIdWithElseThrow(tutorialGroupsConfigurationId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, tutorialGroupsConfiguration.getCourse(), null);

        TutorialGroupFreePeriod newTutorialGroupFreePeriod = new TutorialGroupFreePeriod();
        newTutorialGroupFreePeriod.setTutorialGroupsConfiguration(tutorialGroupsConfiguration);
        newTutorialGroupFreePeriod.setReason(tutorialGroupFreePeriod.reason);

        newTutorialGroupFreePeriod.setStart(interpretInTimeZoneOfConfiguration(tutorialGroupFreePeriod.date, START_OF_DAY, tutorialGroupsConfiguration));
        newTutorialGroupFreePeriod.setEnd(interpretInTimeZoneOfConfiguration(tutorialGroupFreePeriod.date, END_OF_DAY, tutorialGroupsConfiguration));

        var persistedTutorialGroupFreePeriod = tutorialGroupFreePeriodRepository.save(newTutorialGroupFreePeriod);

        tutorialGroupFreePeriodService.cancelActiveOverlappingSessions(tutorialGroupsConfiguration.getCourse(), persistedTutorialGroupFreePeriod);

        return ResponseEntity.created(new URI("/api/tutorial-free-periods/" + persistedTutorialGroupFreePeriod.getId())).body(persistedTutorialGroupFreePeriod);
    }

    /**
     * DELETE /tutorial-group-free-periods/:tutorialGroupFreePeriodId : delete a  tutorial group free period
     *
     * @param tutorialGroupFreePeriodId the id of the tutorial group free period that should be deleted
     * @return ResponseEntity with status 204 (NO_CONTENT)
     */
    @DeleteMapping("/tutorial-group-free-periods/{tutorialGroupFreePeriodId} ")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @FeatureToggle(Feature.TutorialGroups)
    public ResponseEntity<Void> delete(@PathVariable Long tutorialGroupFreePeriodId) throws URISyntaxException {
        log.debug("REST request to delete TutorialGroupFreePeriod: {}", tutorialGroupFreePeriodId);
        TutorialGroupFreePeriod tutorialGroupFreePeriod = tutorialGroupFreePeriodRepository.findByIdElseThrow(tutorialGroupFreePeriodId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, tutorialGroupFreePeriod.getTutorialGroupsConfiguration().getCourse(), null);
        tutorialGroupFreePeriodRepository.delete(tutorialGroupFreePeriod);

        tutorialGroupFreePeriodService.activateCancelledOverlappingSessions(tutorialGroupFreePeriod.getTutorialGroupsConfiguration().getCourse(), tutorialGroupFreePeriod);

        return ResponseEntity.noContent().build();
    }

    record TutorialGroupFreePeriodDTO(Long id, LocalDate date, String reason) {
    }
}
