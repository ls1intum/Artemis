package de.tum.cit.aet.artemis.globalsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityAccessFilterService;

class SearchableEntityAccessFilterServiceTest {

    private final AuthorizationCheckService authCheckService = mock(AuthorizationCheckService.class);

    private final CourseRepository courseRepository = mock(CourseRepository.class);

    private final SearchableEntityAccessFilterService filterService = new SearchableEntityAccessFilterService(authCheckService, courseRepository, Optional.empty());

    private static Course courseWithId(long id) {
        Course course = new Course();
        course.setId(id);
        return course;
    }

    @Test
    void scopesToEveryRequestedCourseNotJustTheFirst() {
        // Regression for the course-scope threading fix: a naive implementation could look up and
        // role-check only the first (or last) requested course id, silently narrowing a multi-course
        // request down to one course instead of the union the caller actually asked for.
        User user = new User();
        user.setId(1L);
        Course courseA = courseWithId(9L);
        Course courseB = courseWithId(11L);
        when(courseRepository.findByIdElseThrow(9L)).thenReturn(courseA);
        when(courseRepository.findByIdElseThrow(11L)).thenReturn(courseB);

        var result = filterService.buildSearchableItemFilter(user, List.of(9L, 11L), List.of(), Set.of(SearchableEntitySchema.TypeValues.EXERCISE));

        assertThat(result.hasAccess()).isTrue();
        assertThat(result.accessibleCoursesById()).containsOnlyKeys(9L, 11L);
        verify(authCheckService).checkHasAtLeastRoleInCourseElseThrow(eq(Role.STUDENT), eq(courseA), eq(user));
        verify(authCheckService).checkHasAtLeastRoleInCourseElseThrow(eq(Role.STUDENT), eq(courseB), eq(user));
    }

    @Test
    void deniesTheWholeRequestWhenTheUserLacksAccessToAnyRequestedCourse() {
        // checkHasAtLeastRoleInCourseElseThrow throws for a course the user cannot access; that must
        // propagate and reject the whole request rather than silently scoping to the courses that did
        // pass, which would leak candidates from an incompletely-checked course.
        User user = new User();
        Course courseA = courseWithId(9L);
        Course courseB = courseWithId(11L);
        when(courseRepository.findByIdElseThrow(9L)).thenReturn(courseA);
        when(courseRepository.findByIdElseThrow(11L)).thenReturn(courseB);
        org.mockito.Mockito.doThrow(new de.tum.cit.aet.artemis.core.exception.AccessForbiddenException("no access")).when(authCheckService)
                .checkHasAtLeastRoleInCourseElseThrow(eq(Role.STUDENT), eq(courseB), eq(user));

        assertThatThrownBy(() -> filterService.buildSearchableItemFilter(user, List.of(9L, 11L), List.of(), Set.of(SearchableEntitySchema.TypeValues.EXERCISE)))
                .isInstanceOf(de.tum.cit.aet.artemis.core.exception.AccessForbiddenException.class);
    }

    @Test
    void fallsBackToAllAccessibleCoursesWhenUnscoped() {
        User user = new User();
        user.setId(1L);
        Course courseA = courseWithId(9L);
        when(courseRepository.findAllAccessibleCoursesForUser(1L, false)).thenReturn(List.of(courseA));

        var result = filterService.buildSearchableItemFilter(user, null, List.of(), Set.of(SearchableEntitySchema.TypeValues.EXERCISE));

        assertThat(result.hasAccess()).isTrue();
        assertThat(result.accessibleCoursesById()).containsOnlyKeys(9L);
        verify(courseRepository, never()).findByIdElseThrow(any());
    }

    @Test
    void unscopedWithNoAccessibleCoursesDeniesAccess() {
        User user = new User();
        user.setId(1L);
        when(courseRepository.findAllAccessibleCoursesForUser(1L, false)).thenReturn(List.of());

        var result = filterService.buildSearchableItemFilter(user, List.of(), List.of(), Set.of(SearchableEntitySchema.TypeValues.EXERCISE));

        assertThat(result.hasAccess()).isFalse();
    }

    @Test
    void excludedCourseIsRemovedFromTheUnscopedFallback() {
        // The scenario this exists for: an unrestricted caller with no courseIds ceiling to narrow
        // itself, so exclusions must be applied to the unscoped fallback rather than dropped.
        User user = new User();
        user.setId(1L);
        Course courseA = courseWithId(9L);
        Course courseB = courseWithId(11L);
        when(courseRepository.findAllAccessibleCoursesForUser(1L, false)).thenReturn(List.of(courseA, courseB));

        var result = filterService.buildSearchableItemFilter(user, null, List.of(11L), Set.of(SearchableEntitySchema.TypeValues.EXERCISE));

        assertThat(result.hasAccess()).isTrue();
        assertThat(result.accessibleCoursesById()).containsOnlyKeys(9L);
    }

    @Test
    void excludingEveryAccessibleCourseDeniesAccess() {
        User user = new User();
        user.setId(1L);
        Course courseA = courseWithId(9L);
        when(courseRepository.findAllAccessibleCoursesForUser(1L, false)).thenReturn(List.of(courseA));

        var result = filterService.buildSearchableItemFilter(user, null, List.of(9L), Set.of(SearchableEntitySchema.TypeValues.EXERCISE));

        assertThat(result.hasAccess()).isFalse();
    }
}
