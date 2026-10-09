package de.tum.cit.aet.artemis.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.dto.CourseManagementDTO;
import de.tum.cit.aet.artemis.lti.test_repository.OnlineCourseConfigurationTestRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class CourseOnlineConfigurationUpdateIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "courseonlineupdate";

    @Autowired
    private OnlineCourseConfigurationTestRepository configurationRepository;

    @Autowired
    private DataSource dataSource;

    private Course course;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 0, 0, 0, 1);
        course = courseUtilService.createEnrolledCourse(TEST_PREFIX);
        course.setEnrollmentEnabled(false);
        course = courseRepository.save(course);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateCourseAddsTheSettingsAnIncompleteCreationLeftOut() throws Exception {
        var jdbc = new JdbcTemplate(dataSource);
        for (String table : new String[] { "online_course_configuration", "tutorial_groups_configuration", "course_iris_settings" }) {
            // The test pool disables auto-commit, so the delete is committed on a connection of its own.
            try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement("DELETE FROM " + table + " WHERE course_id = ?")) {
                connection.setAutoCommit(true);
                statement.setLong(1, course.getId());
                statement.executeUpdate();
            }
        }

        updateCourse(false);

        for (String table : new String[] { "online_course_configuration", "tutorial_groups_configuration", "course_iris_settings" }) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE course_id = ?", Long.class, course.getId())).as(table).isEqualTo(1);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateCourseTogglesConfigurationAndPreservesExistingSettings() throws Exception {
        CourseManagementDTO enabled = updateCourse(true);
        assertThat(enabled.onlineCourse()).isTrue();
        assertThat(enabled.onlineCourseConfiguration()).isNotNull();
        var configuration = configurationRepository.findByCourseId(course.getId()).orElseThrow();
        long configurationId = configuration.getId();
        configuration.setUserPrefix("customprefix");
        configuration.setRequireExistingUser(true);
        configurationRepository.save(configuration);

        updateCourse(true);
        var preserved = configurationRepository.findByCourseId(course.getId()).orElseThrow();
        assertThat(preserved.getId()).isEqualTo(configurationId);
        assertThat(preserved.getUserPrefix()).isEqualTo("customprefix");
        assertThat(preserved.isRequireExistingUser()).isTrue();

        CourseManagementDTO disabled = updateCourse(false);
        assertThat(disabled.onlineCourse()).isFalse();
        assertThat(disabled.onlineCourseConfiguration()).isNull();
        assertThat(configurationRepository.findByCourseId(course.getId())).isEmpty();
        assertThat(courseRepository.findByIdElseThrow(course.getId()).isOnlineCourse()).isFalse();

        updateCourse(true);
        assertThat(courseRepository.findByIdElseThrow(course.getId()).isOnlineCourse()).isTrue();
        var reenabled = configurationRepository.findByCourseId(course.getId()).orElseThrow();
        assertThat(reenabled.getId()).isEqualTo(configurationId);
        assertThat(reenabled.getUserPrefix()).isEqualTo("customprefix");
        assertThat(reenabled.isRequireExistingUser()).isTrue();
    }

    @Test
    void offlineCourseAlreadyHasAStoredConfiguration() {
        assertThat(configurationRepository.findByCourseId(course.getId())).isEmpty();
        var defaults = configurationRepository.findStoredByCourseId(course.getId()).orElseThrow();
        assertThat(defaults.getUserPrefix()).isEqualTo(course.getShortName());
        assertThat(defaults.isRequireExistingUser()).isFalse();
    }

    private CourseManagementDTO updateCourse(boolean online) throws Exception {
        course.setOnlineCourse(online);
        var mapper = request.getObjectMapper();
        var coursePart = new MockMultipartFile("course", "", MediaType.APPLICATION_JSON_VALUE, mapper.writeValueAsBytes(course));
        var builder = MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/course/courses/" + course.getId()).file(coursePart);
        var result = request.performMvcRequest(builder).andExpect(status().isOk()).andReturn();
        return mapper.readValue(result.getResponse().getContentAsString(), CourseManagementDTO.class);
    }

}
