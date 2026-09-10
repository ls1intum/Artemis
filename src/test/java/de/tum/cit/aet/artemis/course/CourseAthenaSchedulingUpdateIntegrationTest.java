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

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseAthenaConfig;
import de.tum.cit.aet.artemis.course.dto.CourseAthenaConfigDTO;
import de.tum.cit.aet.artemis.course.dto.CourseAthenaConfigUpdateDTO;
import de.tum.cit.aet.artemis.course.repository.CourseAthenaConfigRepository;
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

    @Autowired
    private CourseAthenaConfigRepository courseAthenaConfigRepository;

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

    private CourseAthenaConfigDTO updateAthenaConfig(CourseAthenaConfigUpdateDTO update) throws Exception {
        return request.patchWithResponseBody("/api/course/courses/" + course.getId() + "/athena-configuration", update, CourseAthenaConfigDTO.class, HttpStatus.OK);
    }

    /**
     * {@code athenaConfig} is {@code @JsonIgnore} on {@link Course} (see {@link Course#isAthenaGradingFeedbackEnabled()}), so
     * the flag never round-trips back onto a {@code Course} instance deserialized from a response body - only the raw JSON
     * carries it. Returning the parsed tree instead of a {@code Course} lets callers read it directly.
     */
    private JsonNode updateCourse(Course courseToUpdate) throws Exception {
        return updateCourse(courseToUpdate, HttpStatus.OK);
    }

    private JsonNode updateCourse(Course courseToUpdate, HttpStatus expectedStatus) throws Exception {
        ObjectMapper mapper = request.getObjectMapper();
        var coursePart = new MockMultipartFile("course", "", MediaType.APPLICATION_JSON_VALUE, mapper.writeValueAsString(courseToUpdate).getBytes());
        var builder = MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/course/courses/" + courseToUpdate.getId()).file(coursePart)
                .contentType(MediaType.MULTIPART_FORM_DATA_VALUE);
        MvcResult result = request.performMvcRequest(builder).andExpect(status().is(expectedStatus.value())).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateAthenaConfig_enablingGradingFeedback_reschedulesExistingExercises() throws Exception {
        var updated = updateAthenaConfig(new CourseAthenaConfigUpdateDTO(true, null));

        assertThat(updated.gradingFeedbackEnabled()).isTrue();
        verify(instanceMessageSendService).sendProgrammingExerciseSchedule(programmingExercise.getId());
        verify(instanceMessageSendService).sendTextExerciseSchedule(textExercise.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateAthenaConfig_disablingGradingFeedback_reschedulesExistingExercises() throws Exception {
        persistCourseGradingFeedbackEnabled(true);
        reset(instanceMessageSendService);

        var updated = updateAthenaConfig(new CourseAthenaConfigUpdateDTO(false, null));

        assertThat(updated.gradingFeedbackEnabled()).isFalse();
        verify(instanceMessageSendService).sendProgrammingExerciseSchedule(programmingExercise.getId());
        verify(instanceMessageSendService).sendTextExerciseSchedule(textExercise.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateAthenaConfig_gradingFeedbackResentUnchanged_doesNotRescheduleExercises() throws Exception {
        persistCourseGradingFeedbackEnabled(true);
        reset(instanceMessageSendService);

        // Whether the flag changed is decided by the statement that writes it, so a request restating the value it
        // already has updates no row and must not republish scheduling.
        var updated = updateAthenaConfig(new CourseAthenaConfigUpdateDTO(true, null));

        assertThat(updated.gradingFeedbackEnabled()).isTrue();
        verify(instanceMessageSendService, never()).sendProgrammingExerciseSchedule(programmingExercise.getId());
        verify(instanceMessageSendService, never()).sendTextExerciseSchedule(textExercise.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateAthenaConfig_onlyFormativeFeedbackChanged_doesNotRescheduleExercises() throws Exception {
        var updated = updateAthenaConfig(new CourseAthenaConfigUpdateDTO(null, true));

        assertThat(updated.formativeFeedbackEnabled()).isTrue();
        assertThat(updated.gradingFeedbackEnabled()).isFalse();
        verify(instanceMessageSendService, never()).sendProgrammingExerciseSchedule(programmingExercise.getId());
        verify(instanceMessageSendService, never()).sendTextExerciseSchedule(textExercise.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateCourse_doesNotRescheduleExercises() throws Exception {
        // The course update endpoint no longer carries the Athena configuration, so saving the course settings must
        // neither change the flags nor touch Athena scheduling.
        persistCourseGradingFeedbackEnabled(true);
        reset(instanceMessageSendService);

        Course loaded = request.get("/api/course/courses/" + course.getId(), HttpStatus.OK, Course.class);
        loaded.setDescription("Unrelated description change");
        JsonNode updated = updateCourse(loaded);

        assertThat(updated.get("description").asText()).isEqualTo("Unrelated description change");
        // The response must still report the stored flag, so the client does not cache a course that claims Athena is off
        assertThat(updated.get("athenaGradingFeedbackEnabled").asBoolean()).isTrue();
        assertThat(courseRepository.findByIdWithEagerOnlineCourseConfigurationAndTutorialGroupConfigurationElseThrow(course.getId()).getAthenaConfig().isGradingFeedbackEnabled())
                .isTrue();
        verify(instanceMessageSendService, never()).sendProgrammingExerciseSchedule(programmingExercise.getId());
        verify(instanceMessageSendService, never()).sendTextExerciseSchedule(textExercise.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateCourse_courseWithoutConfig_initializesItSoALaterSwitchSurvives() throws Exception {
        // A course from before the configuration existed has a null athena_config_id. The course update gives it a
        // configuration before loading the course, because saving a course loaded without one would write the null
        // back and detach a configuration that a concurrent first switch had attached in between.
        assertThat(courseAthenaConfigRepository.findAthenaConfigIdByCourseId(course.getId())).isEmpty();

        Course loaded = request.get("/api/course/courses/" + course.getId(), HttpStatus.OK, Course.class);
        updateCourse(loaded);

        var configId = courseAthenaConfigRepository.findAthenaConfigIdByCourseId(course.getId());
        assertThat(configId).isPresent();

        // The switch reuses that configuration, and saving the course again leaves it attached.
        updateAthenaConfig(new CourseAthenaConfigUpdateDTO(true, null));
        JsonNode updated = updateCourse(loaded);

        assertThat(courseAthenaConfigRepository.findAthenaConfigIdByCourseId(course.getId())).isEqualTo(configId);
        assertThat(updated.get("athenaGradingFeedbackEnabled").asBoolean()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor2", roles = "INSTRUCTOR")
    void updateCourse_asInstructorOfAnotherCourse_isForbiddenAndCreatesNoConfig() throws Exception {
        // The course update gives a course that predates the Athena configuration one, which must only happen once the
        // user has been authorized for the course: an instructor of another course is rejected without leaving state.
        userUtilService.addInstructor(TEST_PREFIX + "instructor2");
        assertThat(courseAthenaConfigRepository.findAthenaConfigIdByCourseId(course.getId())).isEmpty();

        updateCourse(course, HttpStatus.FORBIDDEN);

        assertThat(courseAthenaConfigRepository.findAthenaConfigIdByCourseId(course.getId())).isEmpty();
    }
}
