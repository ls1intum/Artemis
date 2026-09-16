package de.tum.cit.aet.artemis.admin.management;

import static de.tum.cit.aet.artemis.core.util.RequestUtilService.deleteProgrammingExerciseParamsFalse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.admin.domain.ApplicationAuditEvent;
import de.tum.cit.aet.artemis.admin.domain.PersistentAuditEvent;
import de.tum.cit.aet.artemis.admin.domain.SecurityAuditEvent;
import de.tum.cit.aet.artemis.admin.repository.ApplicationAuditEventRepository;
import de.tum.cit.aet.artemis.admin.repository.PersistenceAuditEventRepository;
import de.tum.cit.aet.artemis.admin.repository.SecurityAuditEventRepository;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.config.audit.AuditEventConstants;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.localci.service.ci.ContinuousIntegrationService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseFactory;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

class ManagementResourceIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "managementresource";

    @Autowired
    private PersistenceAuditEventRepository persistenceAuditEventRepository;

    @Autowired
    private SecurityAuditEventRepository securityAuditEventRepository;

    @Autowired
    private ApplicationAuditEventRepository applicationAuditEventRepository;

    @Autowired
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Autowired
    private FeatureToggleService featureToggleService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    private PersistentAuditEvent persAuditEvent;

    private SecurityAuditEvent securityAuditEvent;

    private ApplicationAuditEvent applicationAuditEvent;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        persAuditEvent = new PersistentAuditEvent();
        persAuditEvent.setPrincipal(TEST_PREFIX + "student1");
        persAuditEvent.setAuditEventDate(Instant.now());
        persAuditEvent.setAuditEventType("type");
        var data = new HashMap<String, String>();
        data.put("1", "2");
        persAuditEvent.setData(data);
        persistenceAuditEventRepository.deleteAll();
        persAuditEvent = persistenceAuditEventRepository.save(persAuditEvent);

        var persAuditEvent2 = new PersistentAuditEvent();
        persAuditEvent2.setPrincipal(TEST_PREFIX + "student2");
        persAuditEvent2.setAuditEventDate(Instant.now().minus(5, ChronoUnit.DAYS));
        persAuditEvent2.setAuditEventType("tt");
        persAuditEvent2.setData(data);
        persistenceAuditEventRepository.save(persAuditEvent2);

        securityAuditEventRepository.deleteAll();
        securityAuditEvent = new SecurityAuditEvent();
        securityAuditEvent.setPrincipal(TEST_PREFIX + "securityprincipal");
        securityAuditEvent.setAuditEventDate(Instant.now());
        securityAuditEvent.setAuditEventType(AuditEventConstants.PASSWORD_RESET_COMPLETED);
        securityAuditEvent.setData(data);
        securityAuditEvent = securityAuditEventRepository.save(securityAuditEvent);

        applicationAuditEventRepository.deleteAll();
        applicationAuditEvent = new ApplicationAuditEvent();
        applicationAuditEvent.setPrincipal(TEST_PREFIX + "applicationprincipal");
        applicationAuditEvent.setAuditEventDate(Instant.now());
        applicationAuditEvent.setAuditEventType(Constants.DELETE_EXERCISE);
        applicationAuditEvent.setData(data);
        applicationAuditEvent = applicationAuditEventRepository.save(applicationAuditEvent);
    }

    @AfterEach
    void tearDown() {
        featureToggleService.enableFeature(Feature.ProgrammingExercises);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void toggleFeatures() throws Exception {
        // This setup only needed in this test case
        var course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExercise(TEST_PREFIX);
        var programmingExercise1 = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        var programmingExercise2 = ProgrammingExerciseFactory.generateProgrammingExercise(ZonedDateTime.now(), ZonedDateTime.now().plusHours(2), course);
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise1, "admin");
        programmingExerciseUtilService.addProgrammingSubmission(programmingExercise1, new ProgrammingSubmission(), "admin");
        doReturn(ContinuousIntegrationService.BuildStatus.BUILDING).when(continuousIntegrationService).getBuildStatus(any());
        doNothing().when(continuousIntegrationService).deleteBuildPlan(any(), any());
        doNothing().when(continuousIntegrationService).deleteProject(any());
        doNothing().when(continuousIntegrationService).updatePlanRepository(any(), any(), any(), any(), any(), any(), any());

        mockTriggerFailedBuild(participation);

        // Try to access 5 different endpoints with programming feature toggle enabled
        request.put("/api/exercise/exercises/" + programmingExercise1.getId() + "/participations/" + participation.getId() + "/resume-programming-participation", null,
                HttpStatus.OK);
        request.put("/api/exercise/participations/" + participation.getId() + "/cleanup-build-plan", null, HttpStatus.OK);
        request.postWithoutLocation("/api/programming/participations/" + participation.getId() + "/trigger-failed-build", null, HttpStatus.OK, null);
        programmingExercise2.setBuildConfig(programmingExerciseBuildConfigRepository.save(programmingExercise2.getBuildConfig()));
        programmingExercise2 = programmingExerciseRepository.save(programmingExercise2);
        request.delete("/api/programming/programming-exercises/" + programmingExercise2.getId(), HttpStatus.OK, deleteProgrammingExerciseParamsFalse());

        var features = new HashMap<Feature, Boolean>();
        features.put(Feature.ProgrammingExercises, false);
        request.put("/api/admin/feature-toggle", features, HttpStatus.OK);
        verify(this.websocketMessagingService).sendMessage("/topic/management/feature-toggles", featureToggleService.enabledFeatures());
        assertThat(featureToggleService.isFeatureEnabled(Feature.ProgrammingExercises)).as("Feature was disabled").isFalse();

        // Try to access 5 different endpoints with programming feature toggle disabled
        request.put("/api/exercise/exercises/" + programmingExercise1.getId() + "/participations/" + participation.getId() + "/resume-programming-participation", null,
                HttpStatus.FORBIDDEN);
        request.put("/api/exercise/participations/" + participation.getId() + "/cleanup-build-plan", null, HttpStatus.FORBIDDEN);
        request.postWithoutLocation("/api/programming/participations/" + participation.getId() + "/trigger-failed-build", null, HttpStatus.FORBIDDEN, null);
        request.delete("/api/programming/programming-exercises/" + programmingExercise1.getId(), HttpStatus.FORBIDDEN);

        // Reset
        featureToggleService.enableFeature(Feature.ProgrammingExercises);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void getAllAuditEvents() throws Exception {
        var auditEvents = request.getList("/api/admin/audits", HttpStatus.OK, PersistentAuditEvent.class);
        assertThat(auditEvents).hasSize(2);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void getAllAuditEventsByDate() throws Exception {
        String pastDate = LocalDate.now().minusDays(1).toString();
        String currentDate = LocalDate.now().toString();
        var auditEvents = request.getList("/api/admin/audits?fromDate=" + pastDate + "&toDate=" + currentDate, HttpStatus.OK, PersistentAuditEvent.class);
        assertThat(auditEvents).hasSize(1);
        var auditEvent = auditEvents.getFirst();
        var auditEventsInDb = persistenceAuditEventRepository.findAllWithDataByAuditEventDateBetween(Instant.now().minus(2, ChronoUnit.DAYS), Instant.now(), Pageable.unpaged());
        assertThat(auditEventsInDb.getTotalElements()).isEqualTo(1);
        assertThat(auditEvent.getPrincipal()).isEqualTo(auditEventsInDb.get().findFirst().orElseThrow().getPrincipal());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void getAllAuditEventsScopedToEachLogType() throws Exception {
        // each tab in the admin UI queries one log; a query must never return another log's rows
        var generalEvents = request.getList("/api/admin/audits?logType=GENERAL", HttpStatus.OK, PersistentAuditEvent.class);
        assertThat(generalEvents).extracting(PersistentAuditEvent::getPrincipal).containsExactlyInAnyOrder(TEST_PREFIX + "student1", TEST_PREFIX + "student2");

        var securityEvents = request.getList("/api/admin/audits?logType=SECURITY", HttpStatus.OK, PersistentAuditEvent.class);
        assertThat(securityEvents).extracting(PersistentAuditEvent::getPrincipal).containsExactly(TEST_PREFIX + "securityprincipal");

        var applicationEvents = request.getList("/api/admin/audits?logType=APPLICATION", HttpStatus.OK, PersistentAuditEvent.class);
        assertThat(applicationEvents).extracting(PersistentAuditEvent::getPrincipal).containsExactly(TEST_PREFIX + "applicationprincipal");
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void getAuditEventByIdIsScopedToTheRequestedLogType() throws Exception {
        var securityEvent = request.get("/api/admin/audits/" + securityAuditEvent.getId() + "?logType=SECURITY", HttpStatus.OK, PersistentAuditEvent.class);
        assertThat(securityEvent.getPrincipal()).isEqualTo(TEST_PREFIX + "securityprincipal");

        var applicationEvent = request.get("/api/admin/audits/" + applicationAuditEvent.getId() + "?logType=APPLICATION", HttpStatus.OK, PersistentAuditEvent.class);
        assertThat(applicationEvent.getPrincipal()).isEqualTo(TEST_PREFIX + "applicationprincipal");
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void getAuditEventByIdDoesNotFallBackToAnotherLog() throws Exception {
        // Each log has its own id sequence, so the same id can exist in more than one of them. Asking for a security
        // event's id under the application log must therefore never answer with the security event: either that id does
        // not exist in the application log (404), or it identifies a different, unrelated application event (200).
        Long securityEventId = securityAuditEvent.getId();
        boolean idAlsoExistsInApplicationLog = applicationAuditEventRepository.findById(securityEventId).isPresent();

        if (idAlsoExistsInApplicationLog) {
            var event = request.get("/api/admin/audits/" + securityEventId + "?logType=APPLICATION", HttpStatus.OK, PersistentAuditEvent.class);
            assertThat(event.getPrincipal()).isNotEqualTo(TEST_PREFIX + "securityprincipal");
        }
        else {
            request.get("/api/admin/audits/" + securityEventId + "?logType=APPLICATION", HttpStatus.NOT_FOUND, PersistentAuditEvent.class);
        }
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void getAllAuditEventsWithUnknownLogTypeIsRejected() throws Exception {
        request.getList("/api/admin/audits?logType=DOES_NOT_EXIST", HttpStatus.BAD_REQUEST, PersistentAuditEvent.class);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void getAuditEvent() throws Exception {
        var auditEvent = request.get("/api/admin/audits/" + persAuditEvent.getId(), HttpStatus.OK, PersistentAuditEvent.class);
        assertThat(auditEvent).isNotNull();
        var auditEventInDb = persistenceAuditEventRepository.findById(persAuditEvent.getId()).orElseThrow();
        assertThat(auditEventInDb.getPrincipal()).isEqualTo(auditEvent.getPrincipal());
    }
}
