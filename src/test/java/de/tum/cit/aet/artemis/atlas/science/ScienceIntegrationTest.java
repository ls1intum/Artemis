package de.tum.cit.aet.artemis.atlas.science;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.atlas.AbstractAtlasIntegrationTest;
import de.tum.cit.aet.artemis.atlas.domain.science.ScienceEvent;
import de.tum.cit.aet.artemis.atlas.domain.science.ScienceEventType;
import de.tum.cit.aet.artemis.atlas.dto.ScienceConsentUpdateDTO;
import de.tum.cit.aet.artemis.atlas.dto.ScienceEventDTO;
import de.tum.cit.aet.artemis.atlas.dto.ScienceResearchExportRequestDTO;
import de.tum.cit.aet.artemis.atlas.repository.ScienceResearchExportAuditRepository;
import de.tum.cit.aet.artemis.atlas.service.ScienceCourseService;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.dto.CourseSelectionDTO;

class ScienceIntegrationTest extends AbstractAtlasIntegrationTest {

    private static final String TEST_PREFIX = "scienceintegration";

    private Course course;

    @Autowired
    private ScienceCourseService scienceCourseService;

    @Autowired
    private ScienceResearchExportAuditRepository scienceResearchExportAuditRepository;

    @BeforeEach
    void enableFeatureToggle() {
        featureToggleService.enableFeature(Feature.Science);
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        course = courseUtilService.addEnrolledEmptyCourse(TEST_PREFIX);
    }

    @AfterEach
    void disableFeatureToggle() {
        featureToggleService.disableFeature(Feature.Science);
    }

    private void sendPutRequest(ScienceEventDTO event) throws Exception {
        request.put("/api/atlas/science", event, HttpStatus.OK);
    }

    @ParameterizedTest
    @EnumSource(value = ScienceEventType.class, names = { "SCIENCE__OPT_IN", "SCIENCE__OPT_OUT", "SCIENCE__DATA_DELETED" }, mode = EnumSource.Mode.EXCLUDE)
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testLogEventOfType(ScienceEventType type) throws Exception {
        scienceCourseService.enableCourse(course.getId());
        scienceCourseService.saveConsentForCurrentUser(course.getId(), true);

        final var event = new ScienceEventDTO(type, 3L, course.getId());
        sendPutRequest(event);
        final var loggedEvents = scienceEventRepository.findAllByType(type).stream().filter(scienceEvent -> course.getId().equals(scienceEvent.getCourseId())).toList();
        assertThat(loggedEvents).hasSize(1);
        final var loggedEvent = loggedEvents.stream().findFirst().get();
        final var principal = SecurityContextHolder.getContext().getAuthentication().getName();
        assertThat(loggedEvent.getIdentity()).isEqualTo(principal);
        assertThat(loggedEvent.getType()).isEqualTo(type);
        assertThat(loggedEvent.getResourceId()).isEqualTo(event.resourceId());
        assertThat(loggedEvent.getCourseId()).isEqualTo(course.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testDoesNotLogWithoutConsent() throws Exception {
        scienceCourseService.enableCourse(course.getId());

        final var event = new ScienceEventDTO(ScienceEventType.EXERCISE__OPEN, 3L, course.getId());
        sendPutRequest(event);
        assertThat(
                scienceEventRepository.findAllByType(ScienceEventType.EXERCISE__OPEN).stream().filter(scienceEvent -> course.getId().equals(scienceEvent.getCourseId())).toList())
                .isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testDoesNotLogClientSuppliedAuditEvent() throws Exception {
        scienceCourseService.enableCourse(course.getId());

        final var event = new ScienceEventDTO(ScienceEventType.SCIENCE__OPT_IN, course.getId(), course.getId());
        sendPutRequest(event);

        assertThat(
                scienceEventRepository.findAllByType(ScienceEventType.SCIENCE__OPT_IN).stream().filter(scienceEvent -> course.getId().equals(scienceEvent.getCourseId())).toList())
                .isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testLogsInitialOptOutDecision() {
        scienceCourseService.enableCourse(course.getId());

        scienceCourseService.saveConsentForCurrentUser(course.getId(), false);

        final var loggedEvents = scienceEventRepository.findAllByType(ScienceEventType.SCIENCE__OPT_OUT).stream()
                .filter(scienceEvent -> course.getId().equals(scienceEvent.getCourseId())).toList();
        assertThat(loggedEvents).hasSize(1);
        final var loggedEvent = loggedEvents.stream().findFirst().get();
        assertThat(loggedEvent.getIdentity()).isEqualTo(TEST_PREFIX + "student1");
        assertThat(loggedEvent.getCourseId()).isEqualTo(course.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testDeleteScienceDataOnlyLogsAuditEventWhenDataWasDeleted() {
        scienceCourseService.enableCourse(course.getId());
        scienceCourseService.saveConsentForCurrentUser(course.getId(), true);

        ScienceEvent scienceEvent = new ScienceEvent();
        scienceEvent.setIdentity(TEST_PREFIX + "student1");
        scienceEvent.setTimestamp(ZonedDateTime.now());
        scienceEvent.setType(ScienceEventType.EXERCISE__OPEN);
        scienceEvent.setResourceId(3L);
        scienceEvent.setCourseId(course.getId());
        scienceEvent = scienceEventRepository.save(scienceEvent);

        scienceCourseService.deleteScienceDataForCurrentUser(course.getId());
        scienceCourseService.deleteScienceDataForCurrentUser(course.getId());

        assertThat(scienceEventRepository.existsById(scienceEvent.getId())).isFalse();
        assertThat(scienceEventRepository.findAllByType(ScienceEventType.SCIENCE__DATA_DELETED).stream()
                .filter(scienceEventEntry -> course.getId().equals(scienceEventEntry.getCourseId())).toList()).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testRecordsOneMarkerPerConsentDecision() {
        scienceCourseService.enableCourse(course.getId());

        scienceCourseService.saveConsentForCurrentUser(course.getId(), true);
        scienceCourseService.saveConsentForCurrentUser(course.getId(), false);
        scienceCourseService.saveConsentForCurrentUser(course.getId(), true);

        assertThat(markersOfType(ScienceEventType.SCIENCE__OPT_IN)).hasSize(2);
        assertThat(markersOfType(ScienceEventType.SCIENCE__OPT_OUT)).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testSavingTheSameDecisionAgainRecordsNoSecondMarker() {
        // The decision is compared against the last recorded marker rather than against the consent row, so re-saving a
        // decision is a no-op for the timeline - and a marker lost to a failed write is repaired by the next save.
        scienceCourseService.enableCourse(course.getId());

        scienceCourseService.saveConsentForCurrentUser(course.getId(), true);
        scienceCourseService.saveConsentForCurrentUser(course.getId(), true);

        assertThat(markersOfType(ScienceEventType.SCIENCE__OPT_IN)).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testRepairsAMarkerThatWasNeverRecorded() {
        // Stands in for the write that failed after the consent row committed: the consent says opted out, the timeline
        // has never said so. Saving the same decision again has to record it rather than conclude nothing changed.
        scienceCourseService.enableCourse(course.getId());
        scienceCourseService.saveConsentForCurrentUser(course.getId(), false);
        scienceEventRepository.deleteAll(markersOfType(ScienceEventType.SCIENCE__OPT_OUT));
        assertThat(markersOfType(ScienceEventType.SCIENCE__OPT_OUT)).isEmpty();

        scienceCourseService.saveConsentForCurrentUser(course.getId(), false);

        assertThat(markersOfType(ScienceEventType.SCIENCE__OPT_OUT)).hasSize(1);
    }

    private List<ScienceEvent> markersOfType(ScienceEventType type) {
        return scienceEventRepository.findAllByType(type).stream().filter(scienceEvent -> course.getId().equals(scienceEvent.getCourseId())).toList();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGetEnabledCourseHistoryLoadsCourseData() {
        scienceCourseService.enableCourse(course.getId());

        var enabledCourseHistory = scienceCourseService.getEnabledCourseHistory();

        assertThat(enabledCourseHistory).anySatisfy(enabledCourse -> {
            assertThat(enabledCourse.courseId()).isEqualTo(course.getId());
            assertThat(enabledCourse.courseTitle()).isEqualTo(course.getTitle());
            assertThat(enabledCourse.courseShortName()).isEqualTo(course.getShortName());
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testGetConsentsForCurrentUserLoadsCourseData() {
        scienceCourseService.enableCourse(course.getId());
        scienceCourseService.saveConsentForCurrentUser(course.getId(), true);

        var consents = scienceCourseService.getConsentsForCurrentUser();

        assertThat(consents).anySatisfy(consent -> {
            assertThat(consent.courseId()).isEqualTo(course.getId());
            assertThat(consent.courseTitle()).isEqualTo(course.getTitle());
            assertThat(consent.courseShortName()).isEqualTo(course.getShortName());
        });
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testEnableAndDisableCourseReturnCourseData() {
        // Regression: save() on a detached entry merges, and the managed copy it returns carries an uninitialized
        // proxy for course. Reading the title off it threw outside the session, so the admin saw an error for a write
        // that had in fact succeeded.
        var enabled = scienceCourseService.enableCourse(course.getId());
        assertThat(enabled.active()).isTrue();
        assertThat(enabled.courseTitle()).isEqualTo(course.getTitle());
        assertThat(enabled.courseShortName()).isEqualTo(course.getShortName());

        var disabled = scienceCourseService.disableCourse(course.getId());
        assertThat(disabled.active()).isFalse();
        assertThat(disabled.courseTitle()).isEqualTo(course.getTitle());
        assertThat(disabled.courseShortName()).isEqualTo(course.getShortName());

        var reEnabled = scienceCourseService.enableCourse(course.getId());
        assertThat(reEnabled.active()).isTrue();
        assertThat(reEnabled.courseTitle()).isEqualTo(course.getTitle());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testCreateResearchExportWithoutDateRange() throws Exception {
        // Regression: the export filtered the optional date range with `(:from IS NULL OR ...)`. PostgreSQL cannot
        // infer a type for a parameter whose only use is `? IS NULL`, so every export failed with a 500 - most
        // obviously the one that leaves the range out entirely.
        persistEvent(ScienceEventType.SCIENCE__OPT_IN, ZonedDateTime.now().minusDays(2));
        ScienceEvent scienceEvent = persistEvent(ScienceEventType.EXERCISE__OPEN, ZonedDateTime.now().minusDays(1));

        var export = scienceCourseService.createResearchExport(new ScienceResearchExportRequestDTO(Set.of(course.getId()), null, null, null, "Regression study"));

        try {
            List<String> lines = Files.readAllLines(export.path(), StandardCharsets.UTF_8);
            assertThat(lines.getFirst()).isEqualTo("identity,timestamp,event_type,course_id,resource_id");
            // The opt-in marker is exported alongside the interaction: it is what tells the researcher when collection
            // was permitted, which is the difference between "did not interact" and "did not agree to be measured".
            assertThat(lines).hasSize(3);
            // The identity is pseudonymized per export, so the login must not appear in the file.
            assertThat(lines).noneMatch(line -> line.contains(scienceEvent.getIdentity()));
            assertThat(lines).anyMatch(line -> line.contains(ScienceEventType.EXERCISE__OPEN.name()) && line.contains(String.valueOf(course.getId())));
            assertThat(export.fileChecksum()).isNotBlank();
            assertThat(export.contentLength()).isEqualTo(Files.size(export.path()));
        }
        finally {
            Files.deleteIfExists(export.path());
        }

        assertThat(scienceResearchExportAuditRepository.findAllByOrderByCreatedDateDesc()).anySatisfy(audit -> {
            assertThat(audit.getPurpose()).isEqualTo("Regression study");
            assertThat(audit.getFilter().courseIds()).containsExactly(course.getId());
            assertThat(audit.getFileChecksum()).isEqualTo(export.fileChecksum());
        });
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testCreateResearchExportAppliesDateRangeAndEventTypeFilters() throws Exception {
        persistEvent(ScienceEventType.SCIENCE__OPT_IN, ZonedDateTime.now().minusDays(20));
        persistEvent(ScienceEventType.EXERCISE__OPEN, ZonedDateTime.now().minusDays(10));
        persistEvent(ScienceEventType.EXERCISE__OPEN, ZonedDateTime.now().minusDays(1));
        persistEvent(ScienceEventType.LECTURE__OPEN, ZonedDateTime.now().minusDays(1));

        var exportRequest = new ScienceResearchExportRequestDTO(Set.of(course.getId()), ZonedDateTime.now().minusDays(2), ZonedDateTime.now(),
                Set.of(ScienceEventType.EXERCISE__OPEN), "Filtered study");
        var export = scienceCourseService.createResearchExport(exportRequest);

        try {
            List<String> lines = Files.readAllLines(export.path(), StandardCharsets.UTF_8);
            assertThat(lines).hasSize(2);
            assertThat(lines.get(1)).contains(ScienceEventType.EXERCISE__OPEN.name());
        }
        finally {
            Files.deleteIfExists(export.path());
        }
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testCreateResearchExportWritesHeaderOnlyWhenNothingMatches() throws Exception {
        var export = scienceCourseService.createResearchExport(new ScienceResearchExportRequestDTO(Set.of(course.getId()), null, null, null, "Empty study"));

        try {
            assertThat(Files.readAllLines(export.path(), StandardCharsets.UTF_8)).containsExactly("identity,timestamp,event_type,course_id,resource_id");
        }
        finally {
            Files.deleteIfExists(export.path());
        }
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testCreateResearchExportExcludesDataCollectedWithoutAConsentDecision() throws Exception {
        // The migration backfills course ids onto events recorded before course-level consent existed, so that a
        // student can delete them per course. Naming the course must not be enough to export them: the former global
        // setting was never consulted by the server, so nothing about those rows records an agreement to be measured.
        persistEvent(ScienceEventType.SCIENCE__OPT_IN, ZonedDateTime.now().minusDays(2));
        persistEvent(ScienceEventType.EXERCISE__OPEN, ZonedDateTime.now().minusDays(1));
        ScienceEvent legacyEvent = persistEventFor(TEST_PREFIX + "student2", ScienceEventType.EXERCISE__OPEN, ZonedDateTime.now().minusDays(1));

        var export = scienceCourseService.createResearchExport(new ScienceResearchExportRequestDTO(Set.of(course.getId()), null, null, null, "Consent-gated study"));

        try {
            List<String> lines = Files.readAllLines(export.path(), StandardCharsets.UTF_8);
            // Two events for two students, only one of whom ever decided, plus that student's marker.
            assertThat(lines).hasSize(3);
            assertThat(scienceEventRepository.existsById(legacyEvent.getId())).isTrue();
        }
        finally {
            Files.deleteIfExists(export.path());
        }
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testCreateResearchExportExcludesDataCollectedBeforeTheDecision() throws Exception {
        // A decision has to predate the event it covers. Asking only whether a decision exists would let a student with
        // backfilled history admit all of it to the export by declining, which is the opposite of what declining means.
        persistEvent(ScienceEventType.EXERCISE__OPEN, ZonedDateTime.now().minusDays(10));
        persistEvent(ScienceEventType.SCIENCE__OPT_OUT, ZonedDateTime.now().minusDays(2));

        var export = scienceCourseService.createResearchExport(new ScienceResearchExportRequestDTO(Set.of(course.getId()), null, null, null, "Declined study"));

        try {
            List<String> lines = Files.readAllLines(export.path(), StandardCharsets.UTF_8);
            // The refusal itself is exported - it is what distinguishes "did not interact" from "did not agree" - but
            // nothing collected before it is.
            assertThat(lines).hasSize(2);
            assertThat(lines.get(1)).contains(ScienceEventType.SCIENCE__OPT_OUT.name());
        }
        finally {
            Files.deleteIfExists(export.path());
        }
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testOffersEveryCourseForSelection() throws Exception {
        var selectableCourses = request.getList("/api/atlas/admin/science/selectable-courses", HttpStatus.OK, CourseSelectionDTO.class);

        assertThat(selectableCourses).anySatisfy(selectableCourse -> {
            assertThat(selectableCourse.id()).isEqualTo(course.getId());
            assertThat(selectableCourse.title()).isEqualTo(course.getTitle());
            assertThat(selectableCourse.shortName()).isEqualTo(course.getShortName());
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testRefusesTheCourseSelectionToNonAdmins() throws Exception {
        request.get("/api/atlas/admin/science/selectable-courses", HttpStatus.FORBIDDEN, List.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testDoesNotLogAfterTheCourseIsDisabled() throws Exception {
        scienceCourseService.enableCourse(course.getId());
        scienceCourseService.saveConsentForCurrentUser(course.getId(), true);
        scienceCourseService.disableCourse(course.getId());

        sendPutRequest(new ScienceEventDTO(ScienceEventType.EXERCISE__OPEN, 3L, course.getId()));

        assertThat(markersOfType(ScienceEventType.EXERCISE__OPEN)).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testRejectsConsentForACourseTheStudentCannotAccess() throws Exception {
        Course foreignCourse = courseUtilService.addEmptyCourse();
        scienceCourseService.enableCourse(foreignCourse.getId());

        request.put("/api/atlas/science/courses/" + foreignCourse.getId() + "/consent", new ScienceConsentUpdateDTO(true), HttpStatus.FORBIDDEN);
        request.delete("/api/atlas/science/courses/" + foreignCourse.getId() + "/data", HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testRejectsConsentForACourseThatDoesNotCollectScienceData() throws Exception {
        request.put("/api/atlas/science/courses/" + course.getId() + "/consent", new ScienceConsentUpdateDTO(true), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void testRefusesTheResearchExportToNonAdmins() throws Exception {
        request.postWithoutLocation("/api/atlas/admin/science/exports", new ScienceResearchExportRequestDTO(Set.of(course.getId()), null, null, null, "Not mine"),
                HttpStatus.FORBIDDEN, null);
        request.put("/api/atlas/admin/science/courses/" + course.getId(), null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testRejectsAnExportWhoseDateRangeReadsBackwards() throws Exception {
        var exportRequest = new ScienceResearchExportRequestDTO(Set.of(course.getId()), ZonedDateTime.now(), ZonedDateTime.now().minusDays(1), null, "Backwards range");

        assertThatThrownBy(() -> scienceCourseService.createResearchExport(exportRequest)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("start date must be before the end date");
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testRejectsAnExportThatAsksForNoEventTypes() throws Exception {
        // Omitting the filter means every type, so an explicitly empty one must not quietly mean the same thing.
        var exportRequest = new ScienceResearchExportRequestDTO(Set.of(course.getId()), null, null, Set.of(), "No types");

        assertThatThrownBy(() -> scienceCourseService.createResearchExport(exportRequest)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("at least one event type");
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testRejectsAnExportWithoutAPurpose() throws Exception {
        // Validated at the boundary rather than at the audit insert: the CSV is generated before the audit is written,
        // so a request that cannot be audited must not get as far as producing one.
        request.postWithoutLocation("/api/atlas/admin/science/exports", new ScienceResearchExportRequestDTO(Set.of(course.getId()), null, null, null, " "), HttpStatus.BAD_REQUEST,
                null);
        request.postWithoutLocation("/api/atlas/admin/science/exports", new ScienceResearchExportRequestDTO(Set.of(), null, null, null, "No courses"), HttpStatus.BAD_REQUEST,
                null);
    }

    private ScienceEvent persistEvent(ScienceEventType type, ZonedDateTime timestamp) {
        return persistEventFor(TEST_PREFIX + "student1", type, timestamp);
    }

    private ScienceEvent persistEventFor(String identity, ScienceEventType type, ZonedDateTime timestamp) {
        ScienceEvent scienceEvent = new ScienceEvent();
        scienceEvent.setIdentity(identity);
        scienceEvent.setTimestamp(timestamp);
        scienceEvent.setType(type);
        scienceEvent.setResourceId(3L);
        scienceEvent.setCourseId(course.getId());
        return scienceEventRepository.save(scienceEvent);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testDeleteCourseDeletesScienceEvents() throws Exception {
        ScienceEvent scienceEvent = new ScienceEvent();
        scienceEvent.setIdentity(TEST_PREFIX + "student1");
        scienceEvent.setTimestamp(ZonedDateTime.now());
        scienceEvent.setType(ScienceEventType.EXERCISE__OPEN);
        scienceEvent.setResourceId(3L);
        scienceEvent.setCourseId(course.getId());
        scienceEvent = scienceEventRepository.save(scienceEvent);

        request.delete("/api/admin/courses/" + course.getId(), HttpStatus.OK);

        assertThat(scienceEventRepository.existsById(scienceEvent.getId())).isFalse();
    }
}
