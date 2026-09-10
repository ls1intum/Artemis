package de.tum.cit.aet.artemis.tutorialgroup.web;

import static de.tum.cit.aet.artemis.core.util.DateUtil.interpretInTimeZone;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import jakarta.validation.Valid;
import jakarta.ws.rs.BadRequestException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.tutorialgroup.config.TutorialGroupEnabled;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupFreePeriod;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupsConfiguration;
import de.tum.cit.aet.artemis.tutorialgroup.dto.TutorialGroupFreePeriodDTO;
import de.tum.cit.aet.artemis.tutorialgroup.dto.TutorialGroupFreePeriodRequestDTO;
import de.tum.cit.aet.artemis.tutorialgroup.dto.TutorialGroupFreePeriodSessionCountDTO;
import de.tum.cit.aet.artemis.tutorialgroup.dto.TutorialGroupSessionCountDTO;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupFreePeriodRepository;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupsConfigurationRepository;
import de.tum.cit.aet.artemis.tutorialgroup.service.TutorialGroupFreePeriodService;

@Conditional(TutorialGroupEnabled.class)
@Lazy
@FeatureUsage("management/free-periods")
@RestController
@RequestMapping("api/tutorialgroup/")
public class TutorialGroupFreePeriodResource {

    private static final String ENTITY_NAME = "tutorialGroupFreePeriod";

    /**
     * Longest span the session counts may be asked for.
     *
     * The page asks for one month's grid, or for the span of a single holiday, so a year is already well past anything
     * it needs. The bound is there because the dates arrive straight from the request and the count materialises one
     * row per session in the span: without it an instructor could ask for every session the course has ever held in one
     * call. It also keeps an extreme date such as {@code +999999999-12-31} from overflowing the exclusive upper bound
     * the count is taken over, which would answer 500 rather than saying the request was wrong.
     */
    private static final long MAX_SESSION_COUNT_SPAN_DAYS = 366;

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
    @GetMapping({ "courses/{courseId}/tutorial-groups-configurations/{tutorialGroupsConfigurationId}/tutorial-free-periods/{tutorialFreePeriodId}",
            "courses/{courseId}/tutorial-groups-configuration/{tutorialGroupsConfigurationId}/tutorial-free-periods/{tutorialFreePeriodId}" })
    @EnforceAtLeastInstructor
    public ResponseEntity<TutorialGroupFreePeriodDTO> getOneOfConfiguration(@PathVariable Long courseId, @PathVariable Long tutorialGroupsConfigurationId,
            @PathVariable("tutorialFreePeriodId") Long tutorialGroupFreePeriodId) {
        log.debug("REST request to get tutorial group free period: {} of tutorial group configuration {} of course: {}", tutorialGroupFreePeriodId, tutorialGroupsConfigurationId,
                courseId);
        var freePeriod = tutorialGroupFreePeriodRepository.findByIdElseThrow(tutorialGroupFreePeriodId);
        checkEntityIdMatchesPathIds(freePeriod, Optional.ofNullable(courseId), Optional.ofNullable(tutorialGroupsConfigurationId));
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, freePeriod.getTutorialGroupsConfiguration().getCourse(), null);
        return ResponseEntity.ok(TutorialGroupFreePeriodDTO.from(freePeriod));
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
    @PutMapping({ "courses/{courseId}/tutorial-groups-configurations/{tutorialGroupsConfigurationId}/tutorial-free-periods/{tutorialFreePeriodId}",
            "courses/{courseId}/tutorial-groups-configuration/{tutorialGroupsConfigurationId}/tutorial-free-periods/{tutorialFreePeriodId}" })
    @EnforceAtLeastInstructor
    public ResponseEntity<TutorialGroupFreePeriodDTO> update(@PathVariable Long courseId, @PathVariable Long tutorialGroupsConfigurationId,
            @PathVariable("tutorialFreePeriodId") Long tutorialGroupFreePeriodId, @RequestBody @Valid TutorialGroupFreePeriodRequestDTO tutorialGroupFreePeriod)
            throws URISyntaxException {
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

        return ResponseEntity.ok(TutorialGroupFreePeriodDTO.from(updatedFreePeriod));
    }

    /**
     * POST courses/:courseId/tutorial-groups-configuration/:tutorialGroupsConfigurationId/tutorial-free-periods : creates a new tutorial group free period
     *
     * @param courseId                      the id of the course to which the tutorial groups configuration belongs
     * @param tutorialGroupsConfigurationId the id of the tutorial groups configuration to which the tutorial group free period should be added
     * @param tutorialGroupFreePeriod       tutorial group free period that should be created
     * @return ResponseEntity with status 201 (Created) and in the body the new tutorial group free period
     */
    @PostMapping({ "courses/{courseId}/tutorial-groups-configurations/{tutorialGroupsConfigurationId}/tutorial-free-periods",
            "courses/{courseId}/tutorial-groups-configuration/{tutorialGroupsConfigurationId}/tutorial-free-periods" })
    @EnforceAtLeastInstructor
    public ResponseEntity<TutorialGroupFreePeriodDTO> create(@PathVariable Long courseId, @PathVariable Long tutorialGroupsConfigurationId,
            @RequestBody @Valid TutorialGroupFreePeriodRequestDTO tutorialGroupFreePeriod) throws URISyntaxException {
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

        return ResponseEntity.created(new URI("/api/tutorialgroup/courses/" + courseId + "/tutorial-groups-configurations/" + tutorialGroupsConfigurationId
                + "/tutorial-free-periods/" + persistedTutorialGroupFreePeriod.getId())).body(TutorialGroupFreePeriodDTO.from(persistedTutorialGroupFreePeriod));
    }

    /**
     * DELETE courses/:courseId/tutorial-groups-configuration/:tutorialGroupsConfigurationId/tutorial-free-periods/tutorial-free-periods : deletes a tutorial free period
     *
     * @param courseId                      the id of the course to which the tutorial groups configuration belongs
     * @param tutorialGroupsConfigurationId the id of the tutorial groups configuration to which the tutorial group free period should be added
     * @param tutorialGroupFreePeriodId     the id of the tutorial group free period that should be deleted
     * @return ResponseEntity with the status 204 (No Content)
     */
    @DeleteMapping({ "courses/{courseId}/tutorial-groups-configurations/{tutorialGroupsConfigurationId}/tutorial-free-periods/{tutorialFreePeriodId}",
            "courses/{courseId}/tutorial-groups-configuration/{tutorialGroupsConfigurationId}/tutorial-free-periods/{tutorialFreePeriodId}" })
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> delete(@PathVariable Long courseId, @PathVariable Long tutorialGroupsConfigurationId,
            @PathVariable("tutorialFreePeriodId") Long tutorialGroupFreePeriodId) throws URISyntaxException {
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

    /**
     * GET courses/:courseId/tutorial-free-periods/session-counts : how many tutorial group sessions the course holds on
     * each day of the requested span.
     * <p>
     * The holidays page shows the number beside every day of the month it displays, and again beside each holiday, so it
     * asks for a whole month at a time instead of one day per request. Days without a session are omitted.
     *
     * @param courseId the id of the course whose sessions are counted
     * @param from     the inclusive first day of the span, in the time zone of the tutorial groups configuration
     * @param to       the inclusive last day of the span, in the time zone of the tutorial groups configuration
     * @return ResponseEntity with status 200 (OK) and the counts of the days that hold at least one session
     */
    @GetMapping("courses/{courseId}/tutorial-free-periods/session-counts")
    @EnforceAtLeastInstructor
    public ResponseEntity<List<TutorialGroupSessionCountDTO>> getSessionCounts(@PathVariable Long courseId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        log.debug("REST request to get tutorial group session counts between {} and {} of course: {}", from, to, courseId);
        if (from.isAfter(to)) {
            throw new BadRequestAlertException("The start of the span must not be after its end", ENTITY_NAME, "invalidDateRange");
        }
        if (ChronoUnit.DAYS.between(from, to) > MAX_SESSION_COUNT_SPAN_DAYS) {
            throw new BadRequestAlertException("The span must not cover more than " + MAX_SESSION_COUNT_SPAN_DAYS + " days", ENTITY_NAME, "spanTooLong");
        }
        TutorialGroupsConfiguration configuration = getConfigurationElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, configuration.getCourse(), null);
        if (configuration.getCourse().getTimeZone() == null) {
            throw new BadRequestException("The course has no time zone");
        }
        ZoneId timeZone = ZoneId.of(configuration.getCourse().getTimeZone());
        return ResponseEntity.ok(tutorialGroupFreePeriodService.countSessionsPerDay(configuration.getCourse(), from, to, timeZone));
    }

    /**
     * GET courses/:courseId/tutorial-free-periods/overlapping-session-count : how many sessions a span would cancel.
     * <p>
     * Counted by overlap, the same test the cancellation applies, so the warning the dialog shows before a holiday is
     * saved matches what saving it does. The per-day counts the calendar is labelled with cannot answer this: a holiday
     * narrowed to part of a day would be credited with the whole day's sessions.
     *
     * @param courseId           the id of the course whose sessions are counted
     * @param from               the start of the span, read as a wall clock in the time zone of the course
     * @param to                 the end of the span
     * @param editedFreePeriodId the holiday being edited, whose own cancelled sessions it would take again; omitted
     *                               when creating one
     * @return ResponseEntity with status 200 (OK) and how many sessions saving would cancel
     */
    @GetMapping("courses/{courseId}/tutorial-free-periods/overlapping-session-count")
    @EnforceAtLeastInstructor
    public ResponseEntity<Long> getOverlappingSessionCount(@PathVariable Long courseId, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to, @RequestParam(required = false) Long editedFreePeriodId) {
        log.debug("REST request to count sessions between {} and {} of course: {}", from, to, courseId);
        if (!from.isBefore(to)) {
            throw new BadRequestAlertException("The start of the span must be before its end", ENTITY_NAME, "invalidDateRange");
        }
        if (ChronoUnit.DAYS.between(from.toLocalDate(), to.toLocalDate()) > MAX_SESSION_COUNT_SPAN_DAYS) {
            throw new BadRequestAlertException("The span must not cover more than " + MAX_SESSION_COUNT_SPAN_DAYS + " days", ENTITY_NAME, "spanTooLong");
        }
        TutorialGroupsConfiguration configuration = getConfigurationElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, configuration.getCourse(), null);
        if (configuration.getCourse().getTimeZone() == null) {
            throw new BadRequestException("The course has no time zone");
        }
        String timeZone = configuration.getCourse().getTimeZone();
        ZonedDateTime start = interpretInTimeZone(from.toLocalDate(), from.toLocalTime(), timeZone);
        ZonedDateTime end = interpretInTimeZone(to.toLocalDate(), to.toLocalTime(), timeZone);
        // Checked again on the instants: a wall clock inside a daylight saving gap moves forward when it is read in a
        // zone, and a span whose start moves further than its end would otherwise be queried inverted.
        if (!start.isBefore(end)) {
            throw new BadRequestAlertException("The start of the span must be before its end in the time zone of the course", ENTITY_NAME, "invalidDateRange");
        }
        return ResponseEntity.ok(tutorialGroupFreePeriodService.countSessionsOverlapping(configuration.getCourse(), start, end, editedFreePeriodId));
    }

    /**
     * GET courses/:courseId/tutorial-free-periods/session-counts-per-period : how many sessions each free period covers.
     * <p>
     * One request for the whole list beside the calendar, rather than one per holiday, and counted by the same overlap
     * so a holiday reports the same number before and after it is saved.
     *
     * @param courseId the id of the course whose free periods are counted
     * @return ResponseEntity with status 200 (OK) and one entry per free period of the course
     */
    @GetMapping("courses/{courseId}/tutorial-free-periods/session-counts-per-period")
    @EnforceAtLeastInstructor
    public ResponseEntity<List<TutorialGroupFreePeriodSessionCountDTO>> getSessionCountsPerFreePeriod(@PathVariable Long courseId) {
        log.debug("REST request to count sessions per free period of course: {}", courseId);
        TutorialGroupsConfiguration configuration = getConfigurationElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, configuration.getCourse(), null);
        return ResponseEntity.ok(tutorialGroupFreePeriodService.countSessionsPerFreePeriod(configuration.getCourse()));
    }

    private TutorialGroupsConfiguration getConfigurationElseThrow(Long courseId) {
        return tutorialGroupsConfigurationRepository.findByCourseIdWithEagerTutorialGroupFreePeriods(courseId)
                .orElseThrow(() -> new BadRequestAlertException("The course has no tutorial groups configuration", ENTITY_NAME, "noConfiguration"));
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
