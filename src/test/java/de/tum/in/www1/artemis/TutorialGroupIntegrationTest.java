package de.tum.in.www1.artemis;

import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import com.google.common.collect.ImmutableSet;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.TutorialGroupRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.util.ModelFactory;

public class TutorialGroupIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    UserRepository userRepository;

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    TutorialGroupRepository tutorialGroupRepository;

    private Long exampleCourseId;

    private Long exampleOneTutorialGroupId;

    private Long exampleTwoTutorialGroupId;

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

        // creating course
        var course = this.database.createCourse();
        exampleCourseId = course.getId();

        // create example tutorial group
        exampleOneTutorialGroupId = createAndSaveTutorialGroup(exampleCourseId, "GuildOfAssassins", userRepository.findOneByLogin("tutor1").get(),
                ImmutableSet.of(userRepository.findOneByLogin("student1").get(), userRepository.findOneByLogin("student2").get(), userRepository.findOneByLogin("student3").get(),
                        userRepository.findOneByLogin("student4").get(), userRepository.findOneByLogin("student5").get())).getId();

        exampleTwoTutorialGroupId = createAndSaveTutorialGroup(exampleCourseId, "GuildOfAlchemists", userRepository.findOneByLogin("tutor2").get(),
                ImmutableSet.of(userRepository.findOneByLogin("student6").get(), userRepository.findOneByLogin("student7").get())).getId();
    }

    private TutorialGroup createAndSaveTutorialGroup(Long courseId, String title, User teachingAssistant, Set<User> registeredStudents) {
        var course = courseRepository.findByIdElseThrow(courseId);
        var tutorialGroup = createTutorialGroup(title, teachingAssistant, registeredStudents, course);
        return tutorialGroupRepository.save(tutorialGroup);
    }

    private TutorialGroup createTutorialGroup(String title, User teachingAssistant, Set<User> registeredStudents, Course course) {
        var tutorialGroup = new TutorialGroup();
        tutorialGroup.setTitle(title);
        tutorialGroup.setCourse(course);
        tutorialGroup.setTeachingAssistant(teachingAssistant);
        tutorialGroup.setRegisteredStudents(registeredStudents);
        return tutorialGroup;
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void getTutorialGroup_asInstructor_shouldReturnTutorialGroup() throws Exception {
        var tutorialGroup = request.get("/api/courses/" + exampleCourseId + "/tutorial-groups/" + exampleOneTutorialGroupId, HttpStatus.OK, TutorialGroup.class);
        assertThat(tutorialGroup.getId()).isEqualTo(exampleOneTutorialGroupId);
    }

    @Test
    @WithMockUser(username = "instructor42", roles = "INSTRUCTOR")
    void getTutorialGroup_asInstructorNotInCourse_shouldReturnForbidden() throws Exception {
        request.get("/api/courses/" + exampleCourseId + "/tutorial-groups/" + exampleOneTutorialGroupId, HttpStatus.FORBIDDEN, TutorialGroup.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void getTutorialGroups_asInstructor_shouldReturnTutorialGroups() throws Exception {
        var tutorialGroupsOfCourse = request.getList("/api/courses/" + exampleCourseId + "/tutorial-groups", HttpStatus.OK, TutorialGroup.class);
        assertThat(tutorialGroupsOfCourse).hasSize(2);
        assertThat(tutorialGroupsOfCourse.stream().map(TutorialGroup::getId).collect(ImmutableSet.toImmutableSet())).containsExactlyInAnyOrder(exampleOneTutorialGroupId,
                exampleTwoTutorialGroupId);
    }

    @Test
    @WithMockUser(username = "instructor42", roles = "INSTRUCTOR")
    void getTutorialGroups_asInstructorNotInCourse_shouldReturnForbidden() throws Exception {
        request.getList("/api/courses/" + exampleCourseId + "/tutorial-groups", HttpStatus.FORBIDDEN, TutorialGroup.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createTutorialGroup_asInstructor_shouldCreateTutorialGroup() throws Exception {
        var course = courseRepository.findWithEagerLearningGoalsById(exampleCourseId).get();
        var tutorialGroup = createTutorialGroup("GuildOfActors", userRepository.findOneByLogin("tutor3").get(),
                ImmutableSet.of(userRepository.findOneByLogin("student8").get(), userRepository.findOneByLogin("student9").get()), course);

        var persistedTutorialGroup = request.postWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups", tutorialGroup, TutorialGroup.class, HttpStatus.CREATED);
        assertThat(persistedTutorialGroup.getId()).isNotNull();
    }

    @Test
    @WithMockUser(username = "instructor42", roles = "INSTRUCTOR")
    void createTutorialGroup_instructorNotInCourse_shouldReturnForbidden() throws Exception {
        var course = courseRepository.findWithEagerLearningGoalsById(exampleCourseId).get();
        var tutorialGroup = createTutorialGroup("GuildOfAlchemists", userRepository.findOneByLogin("tutor3").get(), emptySet(), course);
        request.postWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups", tutorialGroup, TutorialGroup.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createTutorialGroup_tutorialGroupWithTitleAlreadyExists_shouldReturnBadRequest() throws Exception {
        var course = courseRepository.findWithEagerLearningGoalsById(exampleCourseId).get();
        var tutorialGroup = createTutorialGroup("  GuildOfAssassins  ", userRepository.findOneByLogin("tutor3").get(), emptySet(), course);

        request.postWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups", tutorialGroup, TutorialGroup.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createTutorialGroup_tutorialGroupWithEmptyTitle_shouldReturnBadRequest() throws Exception {
        var course = courseRepository.findWithEagerLearningGoalsById(exampleCourseId).get();
        var tutorialGroup = createTutorialGroup("    ", userRepository.findOneByLogin("tutor3").get(), emptySet(), course);

        request.postWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups", tutorialGroup, TutorialGroup.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createTutorialGroup_tutorialGroupWithNullTitle_shouldReturnBadRequest() throws Exception {
        var course = courseRepository.findWithEagerLearningGoalsById(exampleCourseId).get();
        var tutorialGroup = createTutorialGroup(null, userRepository.findOneByLogin("tutor3").get(), emptySet(), course);

        request.postWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups", tutorialGroup, TutorialGroup.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateLearningGoal_asInstructor_shouldUpdateLearningGoal() throws Exception {
        TutorialGroup existingTutorialGroup = tutorialGroupRepository.findByIdWithTeachingAssistantAndRegisteredStudents(exampleOneTutorialGroupId).get();
        existingTutorialGroup.setTitle("Updated");

        TutorialGroup updatedTutorialGroup = request.putWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups", existingTutorialGroup, TutorialGroup.class,
                HttpStatus.OK);

        assertThat(updatedTutorialGroup.getTitle()).isEqualTo("Updated");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateLearningGoal_withNullTitle_shouldReturnBadRequest() throws Exception {
        TutorialGroup existingTutorialGroup = tutorialGroupRepository.findByIdWithTeachingAssistantAndRegisteredStudents(exampleOneTutorialGroupId).get();
        existingTutorialGroup.setTitle(null);

        request.putWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups", existingTutorialGroup, TutorialGroup.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateLearningGoal_withEmptyTitle_shouldReturnBadRequest() throws Exception {
        TutorialGroup existingTutorialGroup = tutorialGroupRepository.findByIdWithTeachingAssistantAndRegisteredStudents(exampleOneTutorialGroupId).get();
        existingTutorialGroup.setTitle("    ");

        request.putWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups", existingTutorialGroup, TutorialGroup.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateLearningGoal_withTitleAlreadyExists_shouldReturnBadRequest() throws Exception {
        TutorialGroup existingTutorialGroup = tutorialGroupRepository.findByIdWithTeachingAssistantAndRegisteredStudents(exampleOneTutorialGroupId).get();
        existingTutorialGroup.setTitle("  GuildOfAlchemists  ");

        request.putWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups", existingTutorialGroup, TutorialGroup.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor42", roles = "INSTRUCTOR")
    void updateTutorialGroup_instructorNotInCourse_shouldReturnForbidden() throws Exception {
        TutorialGroup existingTutorialGroup = tutorialGroupRepository.findByIdWithTeachingAssistantAndRegisteredStudents(exampleOneTutorialGroupId).get();
        existingTutorialGroup.setTitle("Updated");

        request.putWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups", existingTutorialGroup, TutorialGroup.class, HttpStatus.FORBIDDEN);
    }

}
