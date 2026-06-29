package de.tum.cit.aet.artemis.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.atlas.domain.competency.CourseAutoOrchestrationConfiguration;
import de.tum.cit.aet.artemis.atlas.repository.CourseAutoOrchestrationConfigurationRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Verifies that enabling auto-orchestration through the course update endpoint persists the
 * configuration even though the shared {@code findForUpdateById} graph no longer eagerly loads it: the
 * update flow attaches the managed configuration via a dedicated query so {@code applyTo} mutates the
 * existing row in place instead of orphaning it.
 */
class CourseAutoOrchestrationUpdateIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "courseautoorch";

    @Autowired
    private CourseAutoOrchestrationConfigurationRepository autoOrchestrationConfigurationRepository;

    private Course course;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 0, 0, 0, 1);
        course = courseUtilService.createCourseWithUserPrefix(TEST_PREFIX);
    }

    private Course updateCourse(Course courseToUpdate) throws Exception {
        ObjectMapper mapper = request.getObjectMapper();
        var coursePart = new MockMultipartFile("course", "", MediaType.APPLICATION_JSON_VALUE, mapper.writeValueAsString(courseToUpdate).getBytes());
        var builder = MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/course/courses/" + courseToUpdate.getId()).file(coursePart)
                .contentType(MediaType.MULTIPART_FORM_DATA_VALUE);
        MvcResult result = request.performMvcRequest(builder).andExpect(status().isOk()).andReturn();
        return mapper.readValue(result.getResponse().getContentAsString(), Course.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateCourse_enableAutoOrchestration_persistsConfigViaDedicatedQuery() throws Exception {
        // A fresh course has no configuration row at all.
        assertThat(autoOrchestrationConfigurationRepository.findConfigByCourseId(course.getId())).isEmpty();

        // The read-only autoOrchestratorEnabled flag is projected from the (here serialized) configuration.
        var configuration = new CourseAutoOrchestrationConfiguration();
        configuration.setEnabled(true);
        course.setAutoOrchestrationConfiguration(configuration);

        Course updatedCourse = updateCourse(course);

        assertThat(updatedCourse.getAutoOrchestratorEnabled()).isTrue();

        // Verify the configuration was persisted, proving the update path loaded and attached the managed
        // entity without relying on the eager findForUpdateById graph.
        var persisted = autoOrchestrationConfigurationRepository.findConfigByCourseId(course.getId());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().autoOrchestratorEnabled()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateCourse_withExistingConfig_reusesManagedRowInsteadOfOrphaningIt() throws Exception {
        // Pre-persist a configuration row for the course (cascade from the owning course side).
        Course managed = courseRepository.findByIdElseThrow(course.getId());
        var existingConfiguration = new CourseAutoOrchestrationConfiguration();
        existingConfiguration.setEnabled(true);
        existingConfiguration.setDebounceWindowSecondsOverride(120);
        existingConfiguration.setCourse(managed);
        managed.setAutoOrchestrationConfiguration(existingConfiguration);
        courseRepository.save(managed);

        Long originalConfigId = autoOrchestrationConfigurationRepository.findByCourseId(course.getId()).orElseThrow().getId();

        // Update a single field through the endpoint (change the debounce override, keep it enabled).
        var updatedConfiguration = new CourseAutoOrchestrationConfiguration();
        updatedConfiguration.setEnabled(true);
        updatedConfiguration.setDebounceWindowSecondsOverride(300);
        course.setAutoOrchestrationConfiguration(updatedConfiguration);
        updateCourse(course);

        // The attach path must mutate the existing row in place; a broken path would create a new row and
        // orphan the old one (which the insertion-only test above would not catch).
        var persisted = autoOrchestrationConfigurationRepository.findByCourseId(course.getId()).orElseThrow();
        assertThat(persisted.getId()).isEqualTo(originalConfigId);
        assertThat(persisted.isEnabled()).isTrue();
        assertThat(persisted.getDebounceWindowSecondsOverride()).isEqualTo(300);
    }
}
