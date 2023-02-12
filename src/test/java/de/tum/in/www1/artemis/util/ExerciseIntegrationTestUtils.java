package de.tum.in.www1.artemis.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.enumeration.SortingOrder;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;

/**
 * Service responsible for util methods / shared code that is helpful or common for testing all exercise types
 */
@Service
public class ExerciseIntegrationTestUtils {

    @Autowired
    protected DatabaseUtilService database;

    @Autowired
    protected RequestUtilService request;

    public void testCourseAndExamFilters(String apiPath, String searchTerm) throws Exception {
        var search = database.configureSearch(searchTerm);

        // no filter explicitly set -> should default to all filters active and show both exercises
        final var resultWithoutFiltersSet = request.get(apiPath, HttpStatus.OK, SearchResultPageDTO.class, database.searchMapping(search));
        assertThat(resultWithoutFiltersSet.getResultsOnPage()).hasSize(2);

        // both filter explicitly set -> should show both exercises
        final MultiValueMap<String, String> courseAndExamFilterParams = database.searchMapping(search);
        courseAndExamFilterParams.add("isCourseFilter", "true");
        courseAndExamFilterParams.add("isExamFilter", "true");
        final var resultWithCourseAndExamFiltersActive = request.get(apiPath, HttpStatus.OK, SearchResultPageDTO.class, courseAndExamFilterParams);
        assertThat(resultWithCourseAndExamFiltersActive.getResultsOnPage()).hasSize(2);

        // both filter explicitly deactivated -> should show no exercises
        final MultiValueMap<String, String> allFiltersInactiveParams = database.searchMapping(search);
        allFiltersInactiveParams.add("isCourseFilter", "false");
        allFiltersInactiveParams.add("isExamFilter", "false");
        final var resultWithNoFiltersActive = request.get(apiPath, HttpStatus.OK, SearchResultPageDTO.class, allFiltersInactiveParams);
        assertThat(resultWithNoFiltersActive.getResultsOnPage()).isEmpty();

        // only course filter set -> should show only the exercise course
        final MultiValueMap<String, String> courseFilterParams = database.searchMapping(search);
        courseFilterParams.add("isCourseFilter", "true");
        courseFilterParams.add("isExamFilter", "false");
        final var resultWithOnlyCoursesFilterActive = request.get(apiPath, HttpStatus.OK, SearchResultPageDTO.class, courseFilterParams);
        assertThat(resultWithOnlyCoursesFilterActive.getResultsOnPage()).hasSize(1);
        String courseFilterExerciseTitle = ((LinkedHashMap<String, String>) resultWithOnlyCoursesFilterActive.getResultsOnPage().get(0)).get("title");
        assertThat(courseFilterExerciseTitle).isEqualTo(searchTerm);

        // only exam filter set -> should show only the exercise course
        final MultiValueMap<String, String> examFilterParams = database.searchMapping(search);
        examFilterParams.add("isCourseFilter", "false");
        examFilterParams.add("isExamFilter", "true");
        final var resultWithOnlyExamFilterActive = request.get(apiPath, HttpStatus.OK, SearchResultPageDTO.class, examFilterParams);
        assertThat(resultWithOnlyExamFilterActive.getResultsOnPage()).hasSize(1);
        String examFilterExerciseTitle = ((LinkedHashMap<String, String>) resultWithOnlyExamFilterActive.getResultsOnPage().get(0)).get("title");
        assertThat(examFilterExerciseTitle).isEqualTo(searchTerm + "-Morpork");

        for (var sort : Exercise.ExerciseSearchColumn.values()) {
            if (sort == Exercise.ExerciseSearchColumn.PROGRAMMING_LANGUAGE && !apiPath.contains("programming")) {
                continue;
            }
            for (var order : List.of(SortingOrder.ASCENDING, SortingOrder.DESCENDING)) {
                search = database.configureSearch(searchTerm);
                search.setSortedColumn(sort.toString());
                search.setSortingOrder(order);
                var params = database.searchMapping(search);

                if (sort == Exercise.ExerciseSearchColumn.EXAM_TITLE) {
                    params.add("isCourseFilter", "false");
                }
                else if (sort == Exercise.ExerciseSearchColumn.COURSE_TITLE) {
                    params.add("isExamFilter", "false");
                }

                var result = request.get(apiPath, HttpStatus.OK, SearchResultPageDTO.class, params);

                var exerciseComparator = getExpectedComparator(sort);
                if (order == SortingOrder.DESCENDING) {
                    exerciseComparator = exerciseComparator.reversed();
                }
                assertThat(result.getResultsOnPage()).as("Sorting by " + sort + " " + order).isSortedAccordingTo(exerciseComparator);
            }
        }
    }

    private Comparator<LinkedHashMap<String, ? extends Comparable>> getExpectedComparator(Exercise.ExerciseSearchColumn sort) {
        return switch (sort) {
            case ID -> Comparator.comparing(map -> map.get("id"));
            case TITLE -> Comparator.comparing(map -> map.get("title"));
            case COURSE_TITLE -> Comparator.comparing(map -> {
                return ((Map<String, ? extends Comparable>) map.get("course")).get("title");
            });
            case EXAM_TITLE -> Comparator.comparing(map -> {
                var exerciseGroup = (Map<String, ? extends Comparable>) map.get("exerciseGroup");
                var exam = (Map<String, ? extends Comparable>) exerciseGroup.get("exam");
                return exam.get("title");
            });
            case PROGRAMMING_LANGUAGE -> Comparator.comparing(map -> map.get("programmingLanguage"));
        };
    }

}
