package de.tum.cit.aet.artemis.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseAthenaConfig;
import de.tum.cit.aet.artemis.course.dto.CourseManagementDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

/**
 * Verifies that flipping a course's Athena grading feedback flag ({@link CourseAthenaConfig#isGradingFeedbackEnabled()})
 * republishes Athena due-date scheduling for the course's existing exercises.
 * <p>
 * Without this, {@code AthenaScheduleService} only (re)schedules an exercise when it is individually created/updated or
 * on server startup: enabling the flag for an existing course would leave already-existing exercises unscheduled until
 * the next restart, and disabling it would leave an already-scheduled task running and still sending student
 * submissions to Athena.
 */
class CourseAthenaSchedulingUpdateIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "athenaschedupdate";

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    private Course course;

    private ProgrammingExercise programmingExercise;

    private TextExercise textExercise;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 0, 0, 0, 1);
        course = courseUtilService.createEnrolledCourse(TEST_PREFIX);

        programmingExercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course);
        textExercise = textExerciseUtilService.createIndividualTextExercise(course, ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(3),
                ZonedDateTime.now().plusDays(5));

        // Exercise creation above may already interact with the spy; isolate assertions to the update call under test.
        reset(instanceMessageSendService);
    }

    private void persistCourseGradingFeedbackEnabled(boolean enabled) {
        Course persisted = courseRepository.findByIdElseThrow(course.getId());
        CourseAthenaConfig athenaConfig = new CourseAthenaConfig();
        athenaConfig.setCourse(persisted);
        athenaConfig.setGradingFeedbackEnabled(enabled);
        persisted.setAthenaConfig(athenaConfig);
        courseRepository.save(persisted);
    }

    /** Sends a DTO-shaped multipart update and retains the response as JSON for focused field assertions. */
    private JsonNode updateCourse(long courseId, Object courseToUpdate) throws Exception {
        ObjectMapper mapper = request.getObjectMapper();
        var coursePart = new MockMultipartFile("course", "", MediaType.APPLICATION_JSON_VALUE, mapper.writeValueAsString(courseToUpdate).getBytes());
        var builder = MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/course/courses/" + courseId).file(coursePart).contentType(MediaType.MULTIPART_FORM_DATA_VALUE);
        MvcResult result = request.performMvcRequest(builder).andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateCourse_enablingGradingFeedback_reschedulesExistingExercises() throws Exception {
        CourseManagementDTO loaded = request.get("/api/course/courses/" + course.getId(), HttpStatus.OK, CourseManagementDTO.class);
        assertThat(loaded.athenaGradingFeedbackEnabled()).isFalse();
        ObjectNode update = request.getObjectMapper().valueToTree(loaded);
        update.put("athenaGradingFeedbackEnabled", true);
        copyConfigurationToUpdateRequest(update, loaded);

        JsonNode updated = updateCourse(course.getId(), update);

        assertThat(updated.get("athenaGradingFeedbackEnabled").asBoolean()).isTrue();
        verify(instanceMessageSendService).sendProgrammingExerciseSchedule(programmingExercise.getId());
        verify(instanceMessageSendService).sendTextExerciseSchedule(textExercise.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateCourse_disablingGradingFeedback_reschedulesExistingExercises() throws Exception {
        persistCourseGradingFeedbackEnabled(true);
        reset(instanceMessageSendService);

        CourseManagementDTO loaded = request.get("/api/course/courses/" + course.getId(), HttpStatus.OK, CourseManagementDTO.class);
        assertThat(loaded.athenaGradingFeedbackEnabled()).isTrue();
        ObjectNode update = request.getObjectMapper().valueToTree(loaded);
        update.put("athenaGradingFeedbackEnabled", false);
        copyConfigurationToUpdateRequest(update, loaded);

        JsonNode updated = updateCourse(course.getId(), update);

        assertThat(updated.get("athenaGradingFeedbackEnabled").asBoolean()).isFalse();
        verify(instanceMessageSendService).sendProgrammingExerciseSchedule(programmingExercise.getId());
        verify(instanceMessageSendService).sendTextExerciseSchedule(textExercise.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateCourse_unrelatedChange_doesNotRescheduleExercises() throws Exception {
        CourseManagementDTO loaded = request.get("/api/course/courses/" + course.getId(), HttpStatus.OK, CourseManagementDTO.class);
        assertThat(loaded.athenaGradingFeedbackEnabled()).isFalse();
        ObjectNode update = request.getObjectMapper().valueToTree(loaded);
        update.put("description", "Unrelated description change");
        update.put("athenaGradingFeedbackEnabled", false);
        copyConfigurationToUpdateRequest(update, loaded);

        JsonNode updated = updateCourse(course.getId(), update);

        assertThat(updated.get("description").asText()).isEqualTo("Unrelated description change");
        verify(instanceMessageSendService, never()).sendProgrammingExerciseSchedule(programmingExercise.getId());
        verify(instanceMessageSendService, never()).sendTextExerciseSchedule(textExercise.getId());
    }

    private static void copyConfigurationToUpdateRequest(ObjectNode update, CourseManagementDTO course) {
        var configuration = course.courseConfiguration();
        if (configuration != null) {
            update.put("gradeRelevant", configuration.gradeRelevant());
            update.put("dataRetentionHold", configuration.dataRetentionHold());
            update.put("autoOrchestratorEnabled", configuration.autoOrchestratorEnabled());
            update.put("debounceWindowSecondsOverride", configuration.debounceWindowSecondsOverride());
            update.put("maxDailyOrchestrationOverride", configuration.maxDailyOrchestrationOverride());
        }
    }
}
