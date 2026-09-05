package de.tum.cit.aet.artemis.videosource;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.videosource.domain.GocastBindingConnectionStatus;
import de.tum.cit.aet.artemis.videosource.dto.GocastBindingDTO;
import de.tum.cit.aet.artemis.videosource.service.GocastConnectorService;

class GocastIntegrationResourceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "gocastresource";

    private Course course;

    @Autowired
    private ApplicationContext applicationContext;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 2);
        course = courseUtilService.addEmptyCourse();
        userUtilService.addStudentToCourse(TEST_PREFIX + "student1", course);
        userUtilService.addTeachingAssistantToCourse(TEST_PREFIX + "tutor1", course);
        userUtilService.addEditorToCourse(TEST_PREFIX + "editor1", course);
        userUtilService.addInstructorToCourse(TEST_PREFIX + "instructor1", course);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void reportsUnavailableWithoutConstructingTheDisabledConnector() throws Exception {
        GocastBindingDTO result = request.get(endpoint(), HttpStatus.OK, GocastBindingDTO.class);

        assertThat(result.available()).isFalse();
        assertThat(result.status()).isEqualTo(GocastBindingConnectionStatus.UNLINKED);
        assertThat(applicationContext.getBeansOfType(GocastConnectorService.class)).isEmpty();
        request.post(endpoint() + "/approval", null, HttpStatus.SERVICE_UNAVAILABLE);
        request.delete(endpoint(), HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void rejectsEditor() throws Exception {
        request.get(endpoint(), HttpStatus.FORBIDDEN, GocastBindingDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void rejectsTutor() throws Exception {
        request.post(endpoint() + "/approval", null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void rejectsStudent() throws Exception {
        request.delete(endpoint(), HttpStatus.FORBIDDEN);
    }

    @Test
    void rejectsAnonymous() throws Exception {
        request.get(endpoint(), HttpStatus.UNAUTHORIZED, GocastBindingDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor2", roles = "INSTRUCTOR")
    void rejectsInstructorFromAnotherCourse() throws Exception {
        request.get(endpoint(), HttpStatus.FORBIDDEN, GocastBindingDTO.class);
    }

    private String endpoint() {
        return "/api/videosource/courses/" + course.getId() + "/binding";
    }
}
