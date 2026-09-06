package de.tum.cit.aet.artemis.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

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

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseConfiguration;
import de.tum.cit.aet.artemis.course.repository.CourseConfigurationRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Verifies that the data-privacy settings held on a course's {@link CourseConfiguration} survive an ordinary course
 * edit. Both directions matter and both are easy to break by changing a fetch plan:
 * <ul>
 * <li>the read path must return the persisted configuration, otherwise the settings form initializes its controls from
 * the defaults and posts those back;</li>
 * <li>the write path must load the (lazy) configuration, otherwise {@code CourseUpdateDTO.applyTo} attaches a
 * replacement and {@code orphanRemoval} deletes the persisted row along with its retention bookkeeping.</li>
 * </ul>
 */
class CourseConfigurationUpdateIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "courseconfigupdate";

    @Autowired
    private CourseConfigurationRepository courseConfigurationRepository;

    private Course course;

    private ZonedDateTime warningSentDate;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 0, 0, 0, 1);
        course = courseUtilService.createEnrolledCourse(TEST_PREFIX);

        // A course an instructor has opted out of grade relevance for, placed under a data-retention hold (e.g. a
        // pending objection) and already warned about the upcoming student-data reset.
        warningSentDate = ZonedDateTime.now().minusDays(40);
        CourseConfiguration configuration = new CourseConfiguration();
        configuration.setCourse(course);
        configuration.setGradeRelevant(false);
        configuration.setDataRetentionHold(true);
        configuration.setResetWarningSentDate(warningSentDate);
        course.setCourseConfiguration(configuration);
        courseRepository.save(course);
    }

    private Course updateCourse(Course courseToUpdate) throws Exception {
        JsonMapper mapper = request.getObjectMapper();
        var coursePart = new MockMultipartFile("course", "", MediaType.APPLICATION_JSON_VALUE, mapper.writeValueAsString(courseToUpdate).getBytes());
        var builder = MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/course/courses/" + courseToUpdate.getId()).file(coursePart)
                .contentType(MediaType.MULTIPART_FORM_DATA_VALUE);
        MvcResult result = request.performMvcRequest(builder).andExpect(status().isOk()).andReturn();
        return mapper.readValue(result.getResponse().getContentAsString(), Course.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getCourse_asInstructor_shouldExposePersistedDataPrivacySettings() throws Exception {
        Course loaded = request.get("/api/course/courses/" + course.getId(), HttpStatus.OK, Course.class);

        // The settings form reads course.courseConfiguration to initialize its controls, so an absent configuration
        // would silently fall back to the defaults (grade-relevant, not held).
        assertThat(loaded.getCourseConfiguration()).isNotNull();
        assertThat(loaded.getCourseConfiguration().isGradeRelevant()).isFalse();
        assertThat(loaded.getCourseConfiguration().isDataRetentionHold()).isTrue();
        assertThat(loaded.isGradeRelevant()).isFalse();
        assertThat(loaded.isDataRetentionHold()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateCourse_unrelatedChange_shouldPreserveDataPrivacySettingsAndRetentionBookkeeping() throws Exception {
        long configurationId = courseConfigurationRepository.findByCourseId(course.getId()).orElseThrow().getId();

        // Reopen the course the way the settings form does, then save an unrelated field.
        Course loaded = request.get("/api/course/courses/" + course.getId(), HttpStatus.OK, Course.class);
        loaded.setDescription("Unrelated description change");

        Course updated = updateCourse(loaded);

        assertThat(updated.getDescription()).isEqualTo("Unrelated description change");
        assertThat(updated.isGradeRelevant()).isFalse();
        assertThat(updated.isDataRetentionHold()).isTrue();

        CourseConfiguration persisted = courseConfigurationRepository.findByCourseId(course.getId()).orElseThrow();
        assertThat(persisted.isGradeRelevant()).isFalse();
        assertThat(persisted.isDataRetentionHold()).isTrue();
        // Updated in place rather than replaced: a new row would have dropped the retention bookkeeping below.
        assertThat(persisted.getId()).isEqualTo(configurationId);
        assertThat(persisted.getResetWarningSentDate()).isNotNull();
        // Compared with a tolerance because the database rounds to millisecond precision.
        assertThat(persisted.getResetWarningSentDate().toInstant()).isCloseTo(warningSentDate.toInstant(), within(1, ChronoUnit.SECONDS));
    }
}
