package de.tum.in.www1.artemis.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;

import javax.validation.Valid;
import javax.ws.rs.BadRequestException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupsConfiguration;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.TutorialGroupsConfigurationRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggle;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;

@RestController
@RequestMapping("/api")
public class TutorialGroupsConfigurationResource {

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

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
     * GET /courses/:courseId/tutorial-groups-configuration/:tutorialGroupsConfigurationId : gets the tutorial groups configuration with the specified id.
     *
     * @param tutorialGroupsConfigurationId the id of the tutorial groups configuration to retrieve
     * @param courseId                      the id of the course to which the tutorial groups configuration belongs
     * @return ResponseEntity with status 200 (OK) and with body the tutorial groups configuration
     */
    @GetMapping("/courses/{courseId}/tutorial-groups-configuration/{tutorialGroupsConfigurationId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @FeatureToggle(Feature.TutorialGroups)
    public ResponseEntity<TutorialGroupsConfiguration> getOneOfCourse(@PathVariable Long tutorialGroupsConfigurationId, @PathVariable Long courseId) {
        log.debug("REST request to get tutorial groups configuration: {} of course: {}", tutorialGroupsConfigurationId, courseId);

        var configuration = tutorialGroupsConfigurationRepository.findByIdWithElseThrow(tutorialGroupsConfigurationId);
        checkConfigurationCourseIdMatchesPathCourseId(courseId, configuration);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, configuration.getCourse(), null);
        return ResponseEntity.ok().body(configuration);
    }

    /**
     * POST /courses/:courseId/tutorial-groups-configuration : creates a new tutorial group configuration.
     *
     * @param courseId                    the id of the course to which the tutorial group configuration should be added
     * @param tutorialGroupsConfiguration the tutorial group configuration that should be created
     * @return ResponseEntity with status 201 (Created) and in the body the new tutorial group configuration
     */
    @PostMapping("/courses/{courseId}/tutorial-groups-configuration")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @FeatureToggle(Feature.TutorialGroups)
    public ResponseEntity<TutorialGroupsConfiguration> create(@PathVariable Long courseId, @RequestBody @Valid TutorialGroupsConfiguration tutorialGroupsConfiguration)
            throws URISyntaxException {
        log.debug("REST request to create TutorialGroupsConfiguration: {} for course: {}", tutorialGroupsConfiguration, courseId);
        if (tutorialGroupsConfiguration.getId() != null) {
            throw new BadRequestException("A new tutorial group configuration cannot already have an ID");
        }
        var course = courseRepository.findByIdElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);

        if (tutorialGroupsConfigurationRepository.findByCourse(course).isPresent()) {
            throw new BadRequestException("A tutorial group configuration already exists for this course");
        }

        isValidTutorialGroupConfiguration(tutorialGroupsConfiguration);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        tutorialGroupsConfiguration.setCourse(course);
        var persistedConfiguration = tutorialGroupsConfigurationRepository.save(tutorialGroupsConfiguration);
        return ResponseEntity.created(new URI("/api/tutorial-groups-configuration/" + tutorialGroupsConfiguration.getId())).body(persistedConfiguration);
    }

    /**
     * PUT /courses/:courseId/tutorial-groups-configuration/:tutorialGroupsConfigurationId : Updates an existing tutorial groups configuration.
     *
     * @param courseId                          the id of the course to which the tutorial group configuration belongs
     * @param updatedTutorialGroupConfiguration the configuration to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated tutorial group configuration
     */
    @PutMapping("/courses/{courseId}/tutorial-groups-configuration/{tutorialGroupsConfigurationId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @FeatureToggle(Feature.TutorialGroups)
    public ResponseEntity<TutorialGroupsConfiguration> update(@PathVariable Long courseId, @PathVariable Long tutorialGroupsConfigurationId,
            @RequestBody @Valid TutorialGroupsConfiguration updatedTutorialGroupConfiguration) {
        log.debug("REST request to update TutorialGroupsConfiguration: {} for course: {}", updatedTutorialGroupConfiguration, courseId);
        if (updatedTutorialGroupConfiguration.getId() == null) {
            throw new BadRequestException("A tutorial group cannot be updated without an id");
        }
        if (!updatedTutorialGroupConfiguration.getId().equals(tutorialGroupsConfigurationId)) {
            throw new ConflictException("The id of the body must match the id of the path", "TutorialGroupsConfiguration", "tutorialGroupsConfigurationWrongId");
        }
        isValidTutorialGroupConfiguration(updatedTutorialGroupConfiguration);
        var existingConfiguration = this.tutorialGroupsConfigurationRepository.findByIdWithElseThrow(updatedTutorialGroupConfiguration.getId());
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, existingConfiguration.getCourse(), null);
        overrideValues(updatedTutorialGroupConfiguration, existingConfiguration);
        var updatedTutorialGroup = tutorialGroupsConfigurationRepository.save(existingConfiguration);

        // ToDo: Think about how to handle time zone changes logic
        return ResponseEntity.ok(updatedTutorialGroup);
    }

    private static void isValidTutorialGroupConfiguration(TutorialGroupsConfiguration tutorialGroupsConfiguration) {
        // check if time zone code exists
        try {
            ZoneId.of(tutorialGroupsConfiguration.getTimeZone());
        }
        catch (Exception e) {
            throw new BadRequestException("Invalid time zone code");
        }

        if (!isValidDateString(tutorialGroupsConfiguration.getTutorialPeriodStartInclusive()) || !isValidDateString(tutorialGroupsConfiguration.getTutorialPeriodEndInclusive())) {
            throw new BadRequestException("The period must be specified as a date range in the format 'yyyy-MM-dd'");
        }
        if (LocalDate.parse(tutorialGroupsConfiguration.getTutorialPeriodStartInclusive()).isAfter(LocalDate.parse(tutorialGroupsConfiguration.getTutorialPeriodEndInclusive()))) {
            throw new BadRequestException("The start date must be before the end date");
        }
    }

    private void checkConfigurationCourseIdMatchesPathCourseId(Long pathCourseId, TutorialGroupsConfiguration tutorialGroupsConfiguration) {
        if (!tutorialGroupsConfiguration.getCourse().getId().equals(pathCourseId)) {
            throw new ConflictException("The tutorial group configuration does not belong to the correct course", "TutorialGroupsConfiguration",
                    "tutorialGroupsConfigurationWrongCourse");
        }
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
