package de.tum.cit.aet.artemis.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.service.home.CourseHomeTeachingTeamService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

@WithMockUser(username = CourseHomeTeachingTeamTest.PREFIX + "student1", roles = "USER")
class CourseHomeTeachingTeamTest extends AbstractSpringIntegrationIndependentTest {

    static final String PREFIX = "hometeam";

    @Autowired
    private CourseHomeTeachingTeamService team;

    @Autowired
    private de.tum.cit.aet.artemis.account.test_repository.UserTestRepository users;

    private Course course;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(PREFIX, 1, 1, 1, 2);
        course = courseUtilService.createEnrolledCourse(PREFIX);
    }

    @Test
    void projectsOnlyPublicStaffFieldsAndDeduplicatesRoles() {
        var instructor = userUtilService.getUserByLogin(PREFIX + "instructor1");
        userUtilService.enrollUserInCourse(instructor, course, CourseRole.TEACHING_ASSISTANT);
        var featured = team.load(course.getId(), 0, 3);
        assertThat(featured.items()).hasSize(3);
        assertThat(featured.hasMore()).isTrue();
        var expanded = team.load(course.getId(), 0, 6);
        assertThat(expanded.items()).hasSize(4);
        assertThat(expanded.items()).extracting(i -> i.id()).doesNotHaveDuplicates();
        assertThat(expanded.items().getFirst().role()).isEqualTo("INSTRUCTOR");
        assertThat(expanded.hasMore()).isFalse();
        var json = request.getObjectMapper().writeValueAsString(expanded);
        assertThat(json).doesNotContain("email", "registrationNumber", "authorities", PREFIX + "student1");
    }

    @Test
    void profilePicturesUseTheExistingPublicFileContract() {
        var instructor = userUtilService.getUserByLogin(PREFIX + "instructor1");
        instructor.setImageUrl("teacher.png");
        users.saveAndFlush(instructor);
        assertThat(team.load(course.getId(), 0, 6).items()).filteredOn(i -> i.id() == instructor.getId()).singleElement()
                .satisfies(i -> assertThat(i.imageUrl()).isEqualTo("users/" + instructor.getId() + "/profile-pictures/teacher.png"));
    }

    @Test
    void rejectsInvalidPages() {
        assertThatThrownBy(() -> team.load(course.getId(), -1, 6)).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    @WithMockUser(username = PREFIX + "outsider", roles = "USER")
    void rejectsNonmembers() {
        userUtilService.createAndSaveUser(PREFIX + "outsider");
        assertThatThrownBy(() -> team.load(course.getId(), 0, 6)).isInstanceOf(AccessForbiddenException.class);
    }
}
