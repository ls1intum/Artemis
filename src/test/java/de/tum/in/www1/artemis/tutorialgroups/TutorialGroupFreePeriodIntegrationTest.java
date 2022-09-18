package de.tum.in.www1.artemis.tutorialgroups;

import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupScheduleRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupSessionRepository;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;

public class TutorialGroupFreePeriodIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    UserRepository userRepository;

    @Autowired
    DatabaseUtilService databaseUtilService;

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    TutorialGroupRepository tutorialGroupRepository;

    @Autowired
    TutorialGroupSessionRepository tutorialGroupSessionRepository;

    @Autowired
    TutorialGroupScheduleRepository tutorialGroupScheduleRepository;

    Long exampleCourseId;

    Long exampleConfigurationId;

    @AfterEach
    void resetDatabase() {
        database.resetDatabase();
    }

    @BeforeEach
    void setupTestScenario() {
        // creating the users student1-student10, tutor1-tutor10, editor1-editor10 and instructor1-instructor10
        this.database.addUsers(10, 10, 10, 10);

        // Add users that are not in the course
        userRepository.save(ModelFactory.generateActivatedUser("student42"));
        userRepository.save(ModelFactory.generateActivatedUser("tutor42"));
        userRepository.save(ModelFactory.generateActivatedUser("editor42"));
        userRepository.save(ModelFactory.generateActivatedUser("instructor42"));

        var course = this.database.createCourse();
        exampleCourseId = course.getId();

        exampleConfigurationId = databaseUtilService.createTutorialGroupConfiguration(exampleCourseId, "Europe/Bucharest", LocalDate.of(2022, 8, 1), LocalDate.of(2022, 9, 1))
                .getId();
    }

    private void testJustForInstructorEndpoints() throws Exception {
        // Todo
    }

    @Test
    @WithMockUser(value = "instructor42", roles = "INSTRUCTOR")
    void request_asInstructorNotInCourse_shouldReturnForbidden() throws Exception {
        this.testJustForInstructorEndpoints();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void request_asTutor_shouldReturnForbidden() throws Exception {
        this.testJustForInstructorEndpoints();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void request_asStudent_shouldReturnForbidden() throws Exception {
        this.testJustForInstructorEndpoints();
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    void request_asEditor_shouldReturnForbidden() throws Exception {
        this.testJustForInstructorEndpoints();
    }

    ///

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void getOneOfConfiguration_asInstructor_shouldReturnTutorialGroupFreePeriod() throws Exception {
        // Todo
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void create_asInstructor_shouldCreateTutorialGroupFreePeriod() throws Exception {
        // Todo
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void create_invalidAsDateNull_shouldReturnBadRequest() throws Exception {
        // Todo
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void create_overlapsWithExistingScheduledSession_shouldCancelSession() throws Exception {
        // Todo
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void create_overlapsWithExistingIndividualSession_shouldCancelSession() throws Exception {
        // Todo
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void update_asInstructor_shouldActivatePreviouslyCancelledSessionsAndCancelNowOverlappingSessions() throws Exception {
        // Todo
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void delete_asInstructor_shouldActivatePreviouslyCancelledSessions() throws Exception {
        // Todo
    }

}
