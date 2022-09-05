package de.tum.in.www1.artemis.web.rest.tutorialgroups;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.text.SimpleDateFormat;

import javax.validation.Valid;
import javax.ws.rs.BadRequestException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupFreeDay;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupsConfiguration;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupFreeDayRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupsConfigurationRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggle;
import de.tum.in.www1.artemis.service.tutorialgroups.TutorialGroupFreeDayService;

@RestController
@RequestMapping("/api")
public class TutorialGroupFreeDayResource {

    private final Logger log = LoggerFactory.getLogger(TutorialGroupsConfigurationResource.class);

    private final TutorialGroupsConfigurationRepository tutorialGroupsConfigurationRepository;

    private final TutorialGroupFreeDayRepository tutorialGroupFreeDayRepository;

    private final TutorialGroupFreeDayService tutorialGroupFreeDayService;

    private final AuthorizationCheckService authorizationCheckService;

    public TutorialGroupFreeDayResource(TutorialGroupsConfigurationRepository tutorialGroupsConfigurationRepository, CourseRepository courseRepository,
            TutorialGroupFreeDayRepository tutorialGroupFreeDayRepository, TutorialGroupFreeDayService tutorialGroupFreeDayService,
            AuthorizationCheckService authorizationCheckService) {
        this.tutorialGroupsConfigurationRepository = tutorialGroupsConfigurationRepository;
        this.tutorialGroupFreeDayRepository = tutorialGroupFreeDayRepository;
        this.tutorialGroupFreeDayService = tutorialGroupFreeDayService;
        this.authorizationCheckService = authorizationCheckService;
    }

    /**
     * POST /tutorial-groups-configurations/:tutorialGroupsConfigurationId/tutorial-free-days : creates a new tutorial group free day
     *
     * @param tutorialGroupsConfigurationId the id of the tutorial groups configuration to which the tutorial group free day should be added
     * @param tutorialGroupFreeDay          tutorial group free day that should be created
     * @return ResponseEntity with status 201 (Created) and in the body the new tutorial group free day
     */
    @PostMapping("/tutorial-groups-configurations/{tutorialGroupsConfigurationId}/tutorial-free-days")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @FeatureToggle(Feature.TutorialGroups)
    public ResponseEntity<TutorialGroupFreeDay> create(@PathVariable Long tutorialGroupsConfigurationId, @RequestBody @Valid TutorialGroupFreeDay tutorialGroupFreeDay)
            throws URISyntaxException {
        log.debug("REST request to create TutorialGroupFreeDay: {} for tutorial group configuration: {}", tutorialGroupFreeDay, tutorialGroupsConfigurationId);
        if (tutorialGroupFreeDay.getId() != null) {
            throw new BadRequestException("A new tutorial group free day cannot already have an ID");
        }
        TutorialGroupsConfiguration tutorialGroupsConfiguration = tutorialGroupsConfigurationRepository.findByIdWithElseThrow(tutorialGroupsConfigurationId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, tutorialGroupsConfiguration.getCourse(), null);

        tutorialGroupFreeDay.setTutorialGroupsConfiguration(tutorialGroupsConfiguration);
        var persistedTutorialGroupFreeDay = tutorialGroupFreeDayRepository.save(tutorialGroupFreeDay);

        tutorialGroupFreeDayService.cancelActiveOverlappingSessions(persistedTutorialGroupFreeDay);

        return ResponseEntity.created(new URI("/api/tutorial-free-days/" + tutorialGroupFreeDay.getId())).body(persistedTutorialGroupFreeDay);
    }

    /**
     * DELETE /tutorial-group-free-days/:tutorialGroupFreeDayId : delete a  tutorial group free day
     *
     * @param tutorialGroupFreeDayId the id of the tutorial group free day that should be deleted
     * @return ResponseEntity with status 204 (NO_CONTENT)
     */
    @DeleteMapping("/tutorial-group-free-days/{tutorialGroupFreeDayId} ")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @FeatureToggle(Feature.TutorialGroups)
    public ResponseEntity<Void> delete(@PathVariable Long tutorialGroupFreeDayId) throws URISyntaxException {
        log.debug("REST request to delete TutorialGroupFreeDay: {}", tutorialGroupFreeDayId);
        TutorialGroupFreeDay tutorialGroupFreeDay = tutorialGroupFreeDayRepository.findByIdElseThrow(tutorialGroupFreeDayId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, tutorialGroupFreeDay.getTutorialGroupsConfiguration().getCourse(), null);
        tutorialGroupFreeDayRepository.delete(tutorialGroupFreeDay);

        tutorialGroupFreeDayService.activateCancelledOverlappingSessions(tutorialGroupFreeDay);

        return ResponseEntity.noContent().build();
    }

    private static void overrideValues(TutorialGroupsConfiguration sourceConfiguration, TutorialGroupsConfiguration originalConfiguration) {
        originalConfiguration.setTimeZone(sourceConfiguration.getTimeZone());
        originalConfiguration.setTutorialPeriodStartInclusive(sourceConfiguration.getTutorialPeriodStartInclusive());
        originalConfiguration.setTutorialPeriodEndInclusive(sourceConfiguration.getTutorialPeriodEndInclusive());
    }

    private static boolean isValidDateString(String dateString) {
        if (dateString == null || !dateString.matches("\\d{4}-[01]\\d-[0-3]\\d"))
            return false;
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        df.setLenient(false);
        try {
            df.parse(dateString);
            return true;
        }
        catch (ParseException ex) {
            return false;
        }
    }

}
