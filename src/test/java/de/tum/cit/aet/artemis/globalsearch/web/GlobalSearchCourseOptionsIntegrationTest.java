package de.tum.cit.aet.artemis.globalsearch.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;

import tools.jackson.core.type.TypeReference;

import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.dto.CourseForSearchFilterDTO;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;

/**
 * Integration tests for {@code GET /api/search/course-options}: the course filter lists every course the user can search, past
 * courses included, flags which are still active, and offers no course the user is not part of.
 */
@EnabledIf("isWeaviateEnabled")
class GlobalSearchCourseOptionsIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "gscourseopts";

    private static final String URL = "/api/search/course-options";

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private CourseRepository courseRepository;

    private Course activeCourse;

    private Course pastCourse;

    private Course upcomingCourse;

    private Course foreignCourse;

    static boolean isWeaviateEnabled() {
        return weaviateContainer != null && weaviateContainer.isRunning();
    }

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        ZonedDateTime now = ZonedDateTime.now();
        activeCourse = courseUtilService.createEnrolledCourse(TEST_PREFIX);
        pastCourse = withDates(courseUtilService.createEnrolledCourse(TEST_PREFIX), now.minusMonths(6), now.minusDays(10));
        upcomingCourse = withDates(courseUtilService.createEnrolledCourse(TEST_PREFIX), now.plusDays(10), now.plusMonths(6));
        foreignCourse = courseUtilService.createCourse();
    }

    private Course withDates(Course course, ZonedDateTime startDate, ZonedDateTime endDate) {
        course.setStartDate(startDate);
        course.setEndDate(endDate);
        return courseRepository.save(course);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void listsPastCoursesAlongsideActiveOnesAndFlagsWhichHaveEnded() throws Exception {
        List<CourseForSearchFilterDTO> options = request.getList(URL, HttpStatus.OK, CourseForSearchFilterDTO.class);

        assertThat(options).contains(new CourseForSearchFilterDTO(activeCourse.getId(), activeCourse.getTitle(), true),
                new CourseForSearchFilterDTO(pastCourse.getId(), pastCourse.getTitle(), false),
                // Not started yet, but not ended either: ranked with the active courses.
                new CourseForSearchFilterDTO(upcomingCourse.getId(), upcomingCourse.getTitle(), true));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void offersNoCourseTheUserIsNotPartOf() throws Exception {
        List<CourseForSearchFilterDTO> options = request.getList(URL, HttpStatus.OK, CourseForSearchFilterDTO.class);

        assertThat(options).extracting(CourseForSearchFilterDTO::id).doesNotContain(foreignCourse.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void returnsOnlyCourseFilterFieldsAndPreservesFalseInJson() throws Exception {
        List<Map<String, Object>> options = request.get(URL, HttpStatus.OK, new TypeReference<>() {
        });

        assertThat(options).hasSize(3).allSatisfy(option -> {
            assertThat(option).containsOnlyKeys("id", "title", "active");
            assertThat(option.get("id")).isInstanceOf(Number.class);
            assertThat(option.get("title")).isInstanceOf(String.class);
            assertThat(option.get("active")).isInstanceOf(Boolean.class);
        });
        assertThat(options).filteredOn(option -> ((Number) option.get("id")).longValue() == pastCourse.getId()).singleElement()
                .satisfies(option -> assertThat(option).containsEntry("title", pastCourse.getTitle()).containsEntry("active", false));
    }

    @Test
    @WithMockUser(username = "empty" + TEST_PREFIX + "student1", roles = "USER")
    void returnsAnEmptyArrayForAUserWithoutCourses() throws Exception {
        userUtilService.addUsers("empty" + TEST_PREFIX, 1, 0, 0, 0);

        assertThat(request.getList(URL, HttpStatus.OK, CourseForSearchFilterDTO.class)).isEmpty();
    }

    @Test
    @WithAnonymousUser
    void rejectsUnauthenticatedRequests() throws Exception {
        request.get(URL, HttpStatus.UNAUTHORIZED, String.class);
    }
}
