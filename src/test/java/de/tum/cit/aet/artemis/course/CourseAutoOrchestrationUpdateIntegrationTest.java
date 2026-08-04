package de.tum.cit.aet.artemis.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.util.CourseTestService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseConfiguration;
import de.tum.cit.aet.artemis.course.repository.CourseConfigurationRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Verifies that the per-course Atlas auto-orchestration configuration survives the course create and update flows.
 * <p>
 * The settings live on the {@link CourseConfiguration}, which is reached through a lazy {@code Course} association, so
 * the update path only behaves correctly if that association is attached before {@code CourseUpdateDTO.applyTo} runs:
 * otherwise {@code orphanRemoval} replaces the persisted row, and the admin-only change detection compares the submitted
 * values against defaults and rejects unrelated instructor edits. Storing them on the (unconditional) course
 * configuration rather than an Atlas-owned entity is what makes this work with the Atlas module disabled as well.
 */
class CourseAutoOrchestrationUpdateIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "courseautoorch";

    @Autowired
    private CourseConfigurationRepository courseConfigurationRepository;

    @Autowired
    private CourseTestService courseTestService;

    private Course course;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 0, 0, 0, 1);
        course = courseUtilService.createEnrolledCourse(TEST_PREFIX);
    }

    private Course updateCourse(Course courseToUpdate) throws Exception {
        ObjectMapper mapper = request.getObjectMapper();
        var coursePart = new MockMultipartFile("course", "", MediaType.APPLICATION_JSON_VALUE, mapper.writeValueAsString(courseToUpdate).getBytes());
        var builder = MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/course/courses/" + courseToUpdate.getId()).file(coursePart)
                .contentType(MediaType.MULTIPART_FORM_DATA_VALUE);
        MvcResult result = request.performMvcRequest(builder).andExpect(status().isOk()).andReturn();
        return mapper.readValue(result.getResponse().getContentAsString(), Course.class);
    }

    /**
     * Persists an auto-orchestration configuration for the test course, as an admin save would.
     *
     * @param debounceWindowSecondsOverride the debounce override to store, or {@code null} for none
     * @return the id of the persisted configuration row
     */
    private long persistConfiguration(Integer debounceWindowSecondsOverride) {
        Course managed = courseRepository.findByIdElseThrow(course.getId());
        var configuration = courseConfigurationRepository.findByCourseId(course.getId()).orElseGet(CourseConfiguration::new);
        configuration.setAutoOrchestratorEnabled(true);
        configuration.setDebounceWindowSecondsOverride(debounceWindowSecondsOverride);
        configuration.setCourse(managed);
        managed.setCourseConfiguration(configuration);
        courseRepository.save(managed);
        return courseConfigurationRepository.findByCourseId(course.getId()).orElseThrow().getId();
    }

    /**
     * Applies the auto-orchestration settings to the course the way the client does, i.e. as the flat properties the
     * update / create DTO records read off the submitted JSON.
     */
    private static void setAutoOrchestration(Course target, boolean enabled, Integer debounceWindowSecondsOverride, Integer maxDailyOrchestrationOverride) {
        var configuration = target.getCourseConfiguration() != null ? target.getCourseConfiguration() : new CourseConfiguration();
        configuration.setAutoOrchestratorEnabled(enabled);
        configuration.setDebounceWindowSecondsOverride(debounceWindowSecondsOverride);
        configuration.setMaxDailyOrchestrationOverride(maxDailyOrchestrationOverride);
        target.setCourseConfiguration(configuration);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void updateCourse_enableAutoOrchestration_persistsConfiguration() throws Exception {
        // A fresh course created through the test utilities has no configuration row at all.
        assertThat(courseConfigurationRepository.findAutoOrchestrationConfigByCourseId(course.getId())).isEmpty();

        setAutoOrchestration(course, true, null, null);
        updateCourse(course);

        var persisted = courseConfigurationRepository.findAutoOrchestrationConfigByCourseId(course.getId());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().autoOrchestratorEnabled()).isTrue();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void updateCourse_withExistingConfig_reusesManagedRowInsteadOfOrphaningIt() throws Exception {
        long originalConfigId = persistConfiguration(120);

        // Update a single field through the endpoint (change the debounce override, keep it enabled).
        setAutoOrchestration(course, true, 300, null);
        updateCourse(course);

        // The load path must mutate the existing row in place; a broken path would create a new row and orphan the old
        // one (which the insertion-only test above would not catch).
        var persisted = courseConfigurationRepository.findByCourseId(course.getId()).orElseThrow();
        assertThat(persisted.getId()).isEqualTo(originalConfigId);
        assertThat(persisted.isAutoOrchestratorEnabled()).isTrue();
        assertThat(persisted.getDebounceWindowSecondsOverride()).isEqualTo(300);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateCourse_asInstructor_changingAutoOrchestration_isRejected() throws Exception {
        // A fresh course has no configuration row; an instructor attempting to enable auto-orchestration must be rejected.
        assertThat(courseConfigurationRepository.findAutoOrchestrationConfigByCourseId(course.getId())).isEmpty();

        setAutoOrchestration(course, true, null, null);

        ObjectMapper mapper = request.getObjectMapper();
        var coursePart = new MockMultipartFile("course", "", MediaType.APPLICATION_JSON_VALUE, mapper.writeValueAsString(course).getBytes());
        var builder = MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/course/courses/" + course.getId()).file(coursePart).contentType(MediaType.MULTIPART_FORM_DATA_VALUE);
        request.performMvcRequest(builder).andExpect(status().isBadRequest());

        // The setting is admin-only, so no configuration row may be created by the rejected instructor request.
        assertThat(courseConfigurationRepository.findAutoOrchestrationConfigByCourseId(course.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateCourse_asInstructor_unrelatedChange_keepsExistingConfiguration() throws Exception {
        long originalConfigId = persistConfiguration(120);

        // Reopen the course the way the settings form does, then save an unrelated field. The configuration must round
        // trip untouched: if the update path did not load it, the submitted (stored) values would be diffed against the
        // defaults and this instructor edit would be rejected as an admin-only change.
        Course loaded = request.get("/api/course/courses/" + course.getId(), HttpStatus.OK, Course.class);
        assertThat(loaded.getCourseConfiguration()).isNotNull();
        assertThat(loaded.getCourseConfiguration().isAutoOrchestratorEnabled()).isTrue();
        assertThat(loaded.getCourseConfiguration().getDebounceWindowSecondsOverride()).isEqualTo(120);
        loaded.setDescription("Unrelated description change");

        Course updated = updateCourse(loaded);
        assertThat(updated.getDescription()).isEqualTo("Unrelated description change");

        var persisted = courseConfigurationRepository.findByCourseId(course.getId()).orElseThrow();
        assertThat(persisted.getId()).isEqualTo(originalConfigId);
        assertThat(persisted.isAutoOrchestratorEnabled()).isTrue();
        assertThat(persisted.getDebounceWindowSecondsOverride()).isEqualTo(120);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void updateCourse_loadsConfigurationWithoutTheAtlasApi() {
        long originalConfigId = persistConfiguration(120);

        // The update flow reads the settings through this query on the always-active course configuration repository.
        // Asserting it here pins the module-independent load: routing it through the Atlas-conditional
        // CourseAutoOrchestrationApi would silently yield empty when Atlas is disabled, and applyTo would then replace
        // the persisted row.
        var loaded = courseConfigurationRepository.findByCourseId(course.getId());
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getId()).isEqualTo(originalConfigId);
        assertThat(loaded.get().isAutoOrchestratorEnabled()).isTrue();

        // The shared update graph must stay clear of the association: it is already at the query-quality over-fetch
        // budget, so adding a sixth eager path there would fail the Query Quality gate.
        assertThat(courseRepository.findByIdForUpdateElseThrow(course.getId()).getCourseConfiguration()).isNull();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void createCourse_withAutoOrchestration_persistsConfiguration() throws Exception {
        Course newCourse = courseUtilService.createCourse();
        newCourse.setId(null);
        newCourse.setShortName("autoorchcreate");
        setAutoOrchestration(newCourse, true, 600, 5);

        MvcResult result = request.performMvcRequest(courseTestService.buildCreateCourse(newCourse)).andExpect(status().isCreated()).andReturn();
        Course created = request.getObjectMapper().readValue(result.getResponse().getContentAsString(), Course.class);

        var persisted = courseConfigurationRepository.findAutoOrchestrationConfigByCourseId(created.getId()).orElseThrow();
        assertThat(persisted.autoOrchestratorEnabled()).isTrue();
        assertThat(persisted.debounceWindowSecondsOverride()).isEqualTo(600);
        assertThat(persisted.maxDailyOrchestrationOverride()).isEqualTo(5);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void createCourse_withoutAutoOrchestration_leavesThePipelineDisabled() throws Exception {
        Course newCourse = courseUtilService.createCourse();
        newCourse.setId(null);
        newCourse.setShortName("autoorchdefault");

        MvcResult result = request.performMvcRequest(courseTestService.buildCreateCourse(newCourse)).andExpect(status().isCreated()).andReturn();
        Course created = request.getObjectMapper().readValue(result.getResponse().getContentAsString(), Course.class);

        // Every created course gets a configuration row (it also carries the data-retention flags); the pipeline must
        // simply stay disabled with no overrides.
        var persisted = courseConfigurationRepository.findAutoOrchestrationConfigByCourseId(created.getId()).orElseThrow();
        assertThat(persisted.autoOrchestratorEnabled()).isFalse();
        assertThat(persisted.debounceWindowSecondsOverride()).isNull();
        assertThat(persisted.maxDailyOrchestrationOverride()).isNull();
    }
}
