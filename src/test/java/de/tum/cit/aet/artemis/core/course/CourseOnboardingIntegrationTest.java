package de.tum.cit.aet.artemis.core.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class CourseOnboardingIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "courseonboarding";

    private Course course;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 0, 0, 0, 1);

        course = courseUtilService.createCourseWithUserPrefix(TEST_PREFIX);
    }

    private Course updateCourse(Course courseToUpdate) throws Exception {
        ObjectMapper mapper = request.getObjectMapper();
        var coursePart = new MockMultipartFile("course", "", MediaType.APPLICATION_JSON_VALUE, mapper.writeValueAsString(courseToUpdate).getBytes());
        var builder = MockMvcRequestBuilders.multipart(HttpMethod.PUT, "/api/core/courses/" + courseToUpdate.getId()).file(coursePart)
                .contentType(MediaType.MULTIPART_FORM_DATA_VALUE);
        MvcResult result = request.performMvcRequest(builder).andExpect(status().isOk()).andReturn();
        return mapper.readValue(result.getResponse().getContentAsString(), Course.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void newCourse_shouldHaveOnboardingDoneFalse() {
        assertThat(course.isOnboardingDone()).isFalse();

        // Also verify from the database
        Course fromDb = courseRepository.findByIdElseThrow(course.getId());
        assertThat(fromDb.isOnboardingDone()).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateCourse_setOnboardingDoneTrue_shouldPersist() throws Exception {
        course.setOnboardingDone(true);

        Course updatedCourse = updateCourse(course);

        assertThat(updatedCourse.isOnboardingDone()).isTrue();

        // Verify from the database
        Course fromDb = courseRepository.findByIdElseThrow(course.getId());
        assertThat(fromDb.isOnboardingDone()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateCourse_onboardingDoneTrue_cannotBeResetToFalse() throws Exception {
        // First, set onboardingDone to true
        course.setOnboardingDone(true);
        Course updatedCourse = updateCourse(course);
        assertThat(updatedCourse.isOnboardingDone()).isTrue();

        // Now try to reset onboardingDone to false via the update endpoint
        updatedCourse.setOnboardingDone(false);
        Course secondUpdate = updateCourse(updatedCourse);

        // The protection logic should prevent the reset
        assertThat(secondUpdate.isOnboardingDone()).isTrue();

        // Verify from the database that the value is still true
        Course fromDb = courseRepository.findByIdElseThrow(course.getId());
        assertThat(fromDb.isOnboardingDone()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateCourse_onboardingDoneFalse_canBeSetToTrue() throws Exception {
        // Verify initial state is false
        assertThat(course.isOnboardingDone()).isFalse();

        // Update the course with onboardingDone = true
        course.setOnboardingDone(true);
        Course updatedCourse = updateCourse(course);

        assertThat(updatedCourse.isOnboardingDone()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateCourse_onboardingDoneFalse_staysFalseIfNotChanged() throws Exception {
        // Update the course without changing onboardingDone (remains false by default)
        course.setDescription("Updated description");
        Course updatedCourse = updateCourse(course);

        assertThat(updatedCourse.isOnboardingDone()).isFalse();

        Course fromDb = courseRepository.findByIdElseThrow(course.getId());
        assertThat(fromDb.isOnboardingDone()).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateCourse_onboardingDoneTrue_preservedOnUnrelatedUpdate() throws Exception {
        // Set onboardingDone to true
        course.setOnboardingDone(true);
        Course updatedCourse = updateCourse(course);
        assertThat(updatedCourse.isOnboardingDone()).isTrue();

        // Perform an unrelated update (e.g., change description) with onboardingDone still set to true
        updatedCourse.setDescription("New description after onboarding");
        Course secondUpdate = updateCourse(updatedCourse);

        assertThat(secondUpdate.isOnboardingDone()).isTrue();
        assertThat(secondUpdate.getDescription()).isEqualTo("New description after onboarding");
    }
}
