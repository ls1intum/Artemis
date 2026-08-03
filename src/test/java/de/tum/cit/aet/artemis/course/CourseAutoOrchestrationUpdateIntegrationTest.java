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

import de.tum.cit.aet.artemis.atlas.domain.competency.CourseAutoOrchestrationConfiguration;
import de.tum.cit.aet.artemis.atlas.repository.CourseAutoOrchestrationConfigurationRepository;
import de.tum.cit.aet.artemis.core.util.CourseTestService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Verifies that the per-course Atlas auto-orchestration configuration survives the course create and update flows.
 * <p>
 * The configuration row lives in its own table and is reached through the {@code Course} association, so the update
 * path only behaves correctly if that association is attached before {@code CourseUpdateDTO.applyTo} runs: otherwise
 * {@code orphanRemoval} replaces the persisted row, and the admin-only change detection compares the submitted values
 * against defaults and rejects unrelated instructor edits. It is loaded through
 * {@code CourseRepository#findAutoOrchestrationConfigurationByCourseId} — a dedicated query on the always-active course
 * repository — rather than the Atlas-conditional {@code CourseAutoOrchestrationApi}, precisely so this holds with the
 * Atlas module disabled as well, and without adding a sixth eager path to the shared update graph.
 */
class CourseAutoOrchestrationUpdateIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "courseautoorch";

    @Autowired
    private CourseAutoOrchestrationConfigurationRepository autoOrchestrationConfigurationRepository;

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
        var configuration = new CourseAutoOrchestrationConfiguration();
        configuration.setEnabled(true);
        configuration.setDebounceWindowSecondsOverride(debounceWindowSecondsOverride);
        configuration.setCourse(managed);
        managed.setAutoOrchestrationConfiguration(configuration);
        courseRepository.save(managed);
        return courseRepository.findAutoOrchestrationConfigurationByCourseId(course.getId()).orElseThrow().getId();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void updateCourse_enableAutoOrchestration_persistsConfiguration() throws Exception {
        // A fresh course has no configuration row at all.
        assertThat(autoOrchestrationConfigurationRepository.findConfigByCourseId(course.getId())).isEmpty();

        var configuration = new CourseAutoOrchestrationConfiguration();
        configuration.setEnabled(true);
        course.setAutoOrchestrationConfiguration(configuration);

        // getAutoOrchestratorEnabled() is a READ_ONLY JSON projection (dropped on deserialization), so it cannot be
        // asserted on the round-tripped response Course; the persistence check below is the authoritative assertion.
        updateCourse(course);

        var persisted = autoOrchestrationConfigurationRepository.findConfigByCourseId(course.getId());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().autoOrchestratorEnabled()).isTrue();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void updateCourse_withExistingConfig_reusesManagedRowInsteadOfOrphaningIt() throws Exception {
        long originalConfigId = persistConfiguration(120);

        // Update a single field through the endpoint (change the debounce override, keep it enabled).
        var updatedConfiguration = new CourseAutoOrchestrationConfiguration();
        updatedConfiguration.setEnabled(true);
        updatedConfiguration.setDebounceWindowSecondsOverride(300);
        course.setAutoOrchestrationConfiguration(updatedConfiguration);
        updateCourse(course);

        // The load path must mutate the existing row in place; a broken path would create a new row and orphan the old
        // one (which the insertion-only test above would not catch).
        var persisted = courseRepository.findAutoOrchestrationConfigurationByCourseId(course.getId()).orElseThrow();
        assertThat(persisted.getId()).isEqualTo(originalConfigId);
        assertThat(persisted.isEnabled()).isTrue();
        assertThat(persisted.getDebounceWindowSecondsOverride()).isEqualTo(300);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateCourse_asInstructor_changingAutoOrchestration_isRejected() throws Exception {
        // A fresh course has no configuration row; an instructor attempting to enable auto-orchestration must be rejected.
        assertThat(autoOrchestrationConfigurationRepository.findConfigByCourseId(course.getId())).isEmpty();

        var configuration = new CourseAutoOrchestrationConfiguration();
        configuration.setEnabled(true);
        course.setAutoOrchestrationConfiguration(configuration);

        ObjectMapper mapper = request.getObjectMapper();
        var coursePart = new MockMultipartFile("course", "", MediaType.APPLICATION_JSON_VALUE, mapper.writeValueAsString(course).getBytes());
        var builder = MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/course/courses/" + course.getId()).file(coursePart).contentType(MediaType.MULTIPART_FORM_DATA_VALUE);
        request.performMvcRequest(builder).andExpect(status().isBadRequest());

        // The setting is admin-only, so no configuration row may be created by the rejected instructor request.
        assertThat(autoOrchestrationConfigurationRepository.findConfigByCourseId(course.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateCourse_asInstructor_unrelatedChange_keepsExistingConfiguration() throws Exception {
        long originalConfigId = persistConfiguration(120);

        // Reopen the course the way the settings form does, then save an unrelated field. The configuration must round
        // trip untouched: if the update path did not load it, the submitted (stored) values would be diffed against the
        // defaults and this instructor edit would be rejected as an admin-only change.
        Course loaded = request.get("/api/course/courses/" + course.getId(), HttpStatus.OK, Course.class);
        assertThat(loaded.getAutoOrchestratorEnabled()).isTrue();
        assertThat(loaded.getDebounceWindowSecondsOverride()).isEqualTo(120);
        loaded.setDescription("Unrelated description change");

        Course updated = updateCourse(loaded);
        assertThat(updated.getDescription()).isEqualTo("Unrelated description change");

        var persisted = courseRepository.findAutoOrchestrationConfigurationByCourseId(course.getId()).orElseThrow();
        assertThat(persisted.getId()).isEqualTo(originalConfigId);
        assertThat(persisted.isEnabled()).isTrue();
        assertThat(persisted.getDebounceWindowSecondsOverride()).isEqualTo(120);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void updateCourse_loadsConfigurationWithoutTheAtlasApi() {
        long originalConfigId = persistConfiguration(120);

        // The update flow reads the configuration through this query on the always-active CourseRepository. Asserting it
        // here pins the module-independent load: routing it through the Atlas-conditional CourseAutoOrchestrationApi
        // would silently yield empty when Atlas is disabled, and applyTo would then replace the persisted row.
        var loaded = courseRepository.findAutoOrchestrationConfigurationByCourseId(course.getId());
        assertThat(loaded).isPresent();
        assertThat(loaded.get().getId()).isEqualTo(originalConfigId);
        assertThat(loaded.get().isEnabled()).isTrue();

        // The shared update graph must stay clear of the association: it is already at the query-quality over-fetch
        // budget, so adding a sixth eager path there would fail the Query Quality gate.
        assertThat(courseRepository.findByIdForUpdateElseThrow(course.getId()).getAutoOrchestrationConfiguration()).isNull();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void createCourse_withAutoOrchestration_persistsConfiguration() throws Exception {
        Course newCourse = courseUtilService.createCourse();
        newCourse.setId(null);
        newCourse.setShortName("autoorchcreate");
        var configuration = new CourseAutoOrchestrationConfiguration();
        configuration.setEnabled(true);
        configuration.setDebounceWindowSecondsOverride(600);
        configuration.setMaxDailyOrchestrationOverride(5);
        newCourse.setAutoOrchestrationConfiguration(configuration);

        MvcResult result = request.performMvcRequest(courseTestService.buildCreateCourse(newCourse)).andExpect(status().isCreated()).andReturn();
        Course created = request.getObjectMapper().readValue(result.getResponse().getContentAsString(), Course.class);

        var persisted = autoOrchestrationConfigurationRepository.findConfigByCourseId(created.getId()).orElseThrow();
        assertThat(persisted.autoOrchestratorEnabled()).isTrue();
        assertThat(persisted.debounceWindowSecondsOverride()).isEqualTo(600);
        assertThat(persisted.maxDailyOrchestrationOverride()).isEqualTo(5);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void createCourse_withoutAutoOrchestration_createsNoConfigurationRow() throws Exception {
        Course newCourse = courseUtilService.createCourse();
        newCourse.setId(null);
        newCourse.setShortName("autoorchdefault");

        MvcResult result = request.performMvcRequest(courseTestService.buildCreateCourse(newCourse)).andExpect(status().isCreated()).andReturn();
        Course created = request.getObjectMapper().readValue(result.getResponse().getContentAsString(), Course.class);

        // The overwhelming majority of courses never customize the pipeline and must not get an empty configuration row.
        assertThat(autoOrchestrationConfigurationRepository.findConfigByCourseId(created.getId())).isEmpty();
    }
}
