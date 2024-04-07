package de.tum.in.www1.artemis;

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

import de.tum.in.www1.artemis.domain.PersistentAuditEvent;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseFactory;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.PersistenceAuditEventRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.service.connectors.ci.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggleService;
import de.tum.in.www1.artemis.user.UserUtilService;

class ManagementResourceIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "managementresource";

    @Autowired
    private PersistenceAuditEventRepository persistenceAuditEventRepository;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private FeatureToggleService featureToggleService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    private PersistentAuditEvent persAuditEvent;

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
    }

    @AfterEach
    void tearDown() {
        featureToggleService.enableFeature(Feature.ProgrammingExercises);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void toggleFeatures() throws Exception {
        // This setup only needed in this test case
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        var programmingExercise1 = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        var programmingExercise2 = ProgrammingExerciseFactory.generateProgrammingExercise(ZonedDateTime.now(), ZonedDateTime.now().plusHours(2), course);
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise1, "admin");
        programmingExerciseUtilService.addProgrammingSubmission(programmingExercise1, new ProgrammingSubmission(), "admin");
        doReturn(ContinuousIntegrationService.BuildStatus.BUILDING).when(continuousIntegrationService).getBuildStatus(any());
        doNothing().when(continuousIntegrationService).deleteBuildPlan(any(), any());
        doNothing().when(continuousIntegrationService).deleteProject(any());
        doNothing().when(continuousIntegrationService).updatePlanRepository(any(), any(), any(), any(), any(), any(), any());

        mockDefaultBranch(programmingExercise1);
        mockDefaultBranch(programmingExercise2);
        mockTriggerFailedBuild(participation);
        mockGrantReadAccess(participation);

        // Try to access 5 different endpoints with programming feature toggle enabled
        request.put("/api/exercises/" + programmingExercise1.getId() + "/resume-programming-participation/" + participation.getId(), null, HttpStatus.OK);
        request.put("/api/participations/" + participation.getId() + "/cleanupBuildPlan", null, HttpStatus.OK);
        request.postWithoutLocation("/api/programming-submissions/" + participation.getId() + "/trigger-failed-build", null, HttpStatus.OK, null);
        programmingExercise2 = programmingExerciseRepository.save(programmingExercise2);
        request.delete("/api/programming-exercises/" + programmingExercise2.getId(), HttpStatus.OK);

        var features = new HashMap<Feature, Boolean>();
        features.put(Feature.ProgrammingExercises, false);
        request.put("/api/admin/feature-toggle", features, HttpStatus.OK);
        verify(this.websocketMessagingService).sendMessage("/topic/management/feature-toggles", featureToggleService.enabledFeatures());
        assertThat(featureToggleService.isFeatureEnabled(Feature.ProgrammingExercises)).as("Feature was disabled").isFalse();

        // Try to access 5 different endpoints with programming feature toggle disabled
        request.put("/api/exercises/" + programmingExercise1.getId() + "/resume-programming-participation/" + participation.getId(), null, HttpStatus.FORBIDDEN);
        request.put("/api/participations/" + participation.getId() + "/cleanupBuildPlan", null, HttpStatus.FORBIDDEN);
        request.postWithoutLocation("/api/programming-submissions/" + participation.getId() + "/trigger-failed-build", null, HttpStatus.FORBIDDEN, null);
        programmingExercise2 = programmingExerciseRepository.save(programmingExercise2);
        request.delete("/api/programming-exercises/" + programmingExercise2.getId(), HttpStatus.FORBIDDEN);

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
        var auditEvent = auditEvents.get(0);
        var auditEventsInDb = persistenceAuditEventRepository.findAllByAuditEventDateBetween(Instant.now().minus(2, ChronoUnit.DAYS), Instant.now(), Pageable.unpaged());
        assertThat(auditEventsInDb.getTotalElements()).isEqualTo(1);
        assertThat(auditEvent.getPrincipal()).isEqualTo(auditEventsInDb.get().findFirst().orElseThrow().getPrincipal());
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
