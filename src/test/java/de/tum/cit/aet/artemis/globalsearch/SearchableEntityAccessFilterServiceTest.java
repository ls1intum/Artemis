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

    @Test
    void studentExerciseFilterNeverAdmitsExamExercisesWithoutStudentExamData() {
        // filterService is built with Optional.empty() for StudentExamApi (line 32): every test in
        // this class already exercises the "unavailable" path. fetchStudentExamInfo must fail CLOSED
        // there (empty registration/assignment sets), not fall back to a permissive filter that shows
        // every started exam exercise regardless of whether this student is actually assigned to it.
        User user = new User();
        user.setId(1L);
        Course courseA = courseWithId(9L);
        when(courseRepository.findAllAccessibleCoursesForUser(1L, false)).thenReturn(List.of(courseA));

        var result = filterService.buildSearchableItemFilter(user, null, List.of(), Set.of(SearchableEntitySchema.TypeValues.EXERCISE));

        String filter = result.filter().toString();
        assertThat(filter).contains("is_exam_exercise Equal false");
        assertThat(filter).doesNotContain("is_exam_exercise Equal true");
    }

    @Test
    void studentExamFilterOnlyShowsTestExamsWithoutStudentExamData() {
        // Same fail-closed expectation for the exam type disjunct: without registration data, a
        // student sees test exams (always visible) but no regular exam, not every visible exam.
        User user = new User();
        user.setId(1L);
        Course courseA = courseWithId(9L);
        when(courseRepository.findAllAccessibleCoursesForUser(1L, false)).thenReturn(List.of(courseA));

        var result = filterService.buildSearchableItemFilter(user, null, List.of(), Set.of(SearchableEntitySchema.TypeValues.EXAM));

        String filter = result.filter().toString();
        assertThat(filter).contains("test_exam Equal true");
        assertThat(filter).doesNotContain("entity_id ContainsAny");
    }

    @Test
    void hiddenTypesSuppressesTheExamExerciseAutoInclusion() {
        // Parity with the palette (GlobalSearchResource): a caller that explicitly hid exercises must
        // not have them reappear via the exam type's own auto-inclusion of exam exercises, even though
        // "exercise" is equally absent from requestedTypes in both the "not asked for" and "hidden" case.
        User user = new User();
        user.setId(1L);
        Course courseA = courseWithId(9L);
        when(courseRepository.findAllAccessibleCoursesForUser(1L, false)).thenReturn(List.of(courseA));

        var result = filterService.buildSearchableItemFilter(user, null, List.of(), Set.of(SearchableEntitySchema.TypeValues.EXAM),
                Set.of(SearchableEntitySchema.TypeValues.EXERCISE), true);

        assertThat(result.hasAccess()).isTrue();
        assertThat(result.filter().toString()).doesNotContain("type Equal exercise");
    }

    @Test
    void lenientCourseIdsDropsAnInaccessibleOneInsteadOfThrowing() {
        // Parity with the palette (GlobalSearchResource): rejectInaccessibleCourseIds=false must not
        // reach findByIdElseThrow/checkHasAtLeastRoleInCourseElseThrow at all for a scoped request; an
        // inaccessible or unknown id is silently absent from the batch lookup's result instead.
        User user = new User();
        user.setId(1L);
        Course courseA = courseWithId(9L);
        when(courseRepository.findAllAccessibleCoursesForUserAndIdIn(1L, false, Set.of(9L, 11L))).thenReturn(List.of(courseA));

        var result = filterService.buildSearchableItemFilter(user, List.of(9L, 11L), List.of(), Set.of(SearchableEntitySchema.TypeValues.EXERCISE), Set.of(), false);

        assertThat(result.hasAccess()).isTrue();
        assertThat(result.accessibleCoursesById()).containsOnlyKeys(9L);
        verify(courseRepository, never()).findByIdElseThrow(any());
        verify(authCheckService, never()).checkHasAtLeastRoleInCourseElseThrow(any(), any(), any());
    }

    @Test
    void adminExclusionAppliesToTheUnscopedTypeOnlyFilter() {
        // An admin with no courseIds ceiling skips role classification entirely for most types
        // (the isAdmin && !hasCourseScope branch uses a bare type filter, no per-course role set to
        // apply an exclusion through) — accessibleCourses is narrowed by excludeCourseIds, but that
        // narrowing must also reach the actual Weaviate filter, not just the role/course-name bookkeeping.
        when(authCheckService.isCurrentUserAdminAccessEnabled()).thenReturn(true);
        when(courseRepository.findAll()).thenReturn(List.of(courseWithId(9L), courseWithId(5L)));
        User user = new User();
        user.setId(1L);

        var result = filterService.buildSearchableItemFilter(user, null, List.of(5L), Set.of(SearchableEntitySchema.TypeValues.EXERCISE));

        assertThat(result.hasAccess()).isTrue();
        String filter = result.filter().toString();
        assertThat(filter).contains("type Equal exercise");
        // The negated clause excluding course 5, not merely a mention of it.
        assertThat(filter).containsPattern("[Nn]ot?e? Filter\\(course_id Equal 5\\)");
    }
}
