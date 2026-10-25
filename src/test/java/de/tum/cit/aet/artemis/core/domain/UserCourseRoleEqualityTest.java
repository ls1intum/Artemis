package de.tum.cit.aet.artemis.core.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.course.domain.Course;

class UserCourseRoleEqualityTest {

    @Test
    void membershipIdentityUsesUserCourseAndRoleIdsAcrossEntityInstances() {
        var first = membership(1L, 2L, CourseRole.STUDENT);
        var equivalent = membership(1L, 2L, CourseRole.STUDENT);
        assertThat(first).isEqualTo(first).isEqualTo(equivalent).isNotEqualTo(null).isNotEqualTo("not membership");
        assertThat(first.hashCode()).isEqualTo(equivalent.hashCode());
        var memberships = new HashSet<UserCourseRole>();
        memberships.add(first);
        memberships.add(equivalent);
        memberships.add(membership(2L, 2L, CourseRole.STUDENT));
        memberships.add(membership(1L, 3L, CourseRole.STUDENT));
        memberships.add(membership(1L, 2L, CourseRole.INSTRUCTOR));
        assertThat(memberships).hasSize(4);
        assertThat(first.getUser().getId()).isEqualTo(1L);
        assertThat(first.getCourse().getId()).isEqualTo(2L);
        assertThat(first.getRole()).isEqualTo(CourseRole.STUDENT);
    }

    @Test
    void incompleteMembershipsHaveStableNullSafeEquality() {
        var empty = new UserCourseRole();
        assertThat(empty).isEqualTo(new UserCourseRole());
        assertThat(empty.hashCode()).isEqualTo(new UserCourseRole().hashCode());
        assertThat(empty).isNotEqualTo(membership(1L, null, null)).isNotEqualTo(membership(null, 2L, null));
        assertThat(membership(1L, null, null)).isNotEqualTo(empty);
        assertThat(membership(null, 2L, null)).isNotEqualTo(empty);
        assertThat(membership(1L, 2L, CourseRole.STUDENT)).isNotEqualTo(empty);
        assertThat(membership(1L, null, CourseRole.STUDENT)).isEqualTo(membership(1L, null, CourseRole.STUDENT));
        assertThat(membership(null, 2L, CourseRole.STUDENT)).isEqualTo(membership(null, 2L, CourseRole.STUDENT));
    }

    @Test
    void compositeIdsDeduplicateOnlyExactMemberships() {
        var first = new UserCourseRole.UserCourseRoleId(1L, 2L, CourseRole.STUDENT);
        var equal = new UserCourseRole.UserCourseRoleId(1L, 2L, CourseRole.STUDENT);
        assertThat(first).isEqualTo(first).isEqualTo(equal).isNotEqualTo(null).isNotEqualTo("id");
        assertThat(first.hashCode()).isEqualTo(equal.hashCode());
        assertThat(first).isNotEqualTo(new UserCourseRole.UserCourseRoleId(2L, 2L, CourseRole.STUDENT))
                .isNotEqualTo(new UserCourseRole.UserCourseRoleId(1L, 3L, CourseRole.STUDENT)).isNotEqualTo(new UserCourseRole.UserCourseRoleId(1L, 2L, CourseRole.EDITOR));
        assertThat(new UserCourseRole.UserCourseRoleId()).isEqualTo(new UserCourseRole.UserCourseRoleId());
    }

    private UserCourseRole membership(Long userId, Long courseId, CourseRole role) {
        User user = null;
        Course course = null;
        if (userId != null) {
            user = new User();
            user.setId(userId);
        }
        if (courseId != null) {
            course = new Course();
            course.setId(courseId);
        }
        return new UserCourseRole(user, course, role);
    }
}
