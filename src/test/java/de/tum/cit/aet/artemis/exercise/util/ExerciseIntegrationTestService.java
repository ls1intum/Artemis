package de.tum.cit.aet.artemis.exercise.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;

import de.tum.cit.aet.artemis.core.dto.SortingOrder;
import de.tum.cit.aet.artemis.core.util.PageUtil;
import de.tum.cit.aet.artemis.core.util.PageableSearchUtilService;
import de.tum.cit.aet.artemis.core.util.RequestUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Service responsible for util methods / shared code that is helpful or common for testing all exercise types
 */
@Service
public class ExerciseIntegrationTestService {

    @Autowired
    protected PageableSearchUtilService pageableSearchUtilService;

    @Autowired
    protected RequestUtilService request;

    public void testCourseAndExamFilters(String apiPath, String searchTerm) throws Exception {
        var search = pageableSearchUtilService.configureSearch(searchTerm);

        // no filter explicitly set -> should default to all filters active and show both exercises
        final var resultWithoutFiltersSet = request.getSearchResult(apiPath, HttpStatus.OK, Exercise.class, pageableSearchUtilService.searchMapping(search));
        assertThat(resultWithoutFiltersSet.getResultsOnPage()).hasSize(2);

        // both filter explicitly set -> should show both exercises
        final MultiValueMap<String, String> courseAndExamFilterParams = pageableSearchUtilService.searchMapping(search);
        courseAndExamFilterParams.add("isCourseFilter", "true");
        courseAndExamFilterParams.add("isExamFilter", "true");
        final var resultWithCourseAndExamFiltersActive = request.getSearchResult(apiPath, HttpStatus.OK, Exercise.class, courseAndExamFilterParams);
        assertThat(resultWithCourseAndExamFiltersActive.getResultsOnPage()).hasSize(2);

        // both filter explicitly deactivated -> should show no exercises
        final MultiValueMap<String, String> allFiltersInactiveParams = pageableSearchUtilService.searchMapping(search);
        allFiltersInactiveParams.add("isCourseFilter", "false");
        allFiltersInactiveParams.add("isExamFilter", "false");
        final var resultWithNoFiltersActive = request.getSearchResult(apiPath, HttpStatus.OK, Exercise.class, allFiltersInactiveParams);
        assertThat(resultWithNoFiltersActive.getResultsOnPage()).isEmpty();

        // only course filter set -> should show only the exercise course
        final MultiValueMap<String, String> courseFilterParams = pageableSearchUtilService.searchMapping(search);
        courseFilterParams.add("isCourseFilter", "true");
        courseFilterParams.add("isExamFilter", "false");
        final var resultWithOnlyCoursesFilterActive = request.getSearchResult(apiPath, HttpStatus.OK, Exercise.class, courseFilterParams);
        assertThat(resultWithOnlyCoursesFilterActive.getResultsOnPage()).hasSize(1);
        String courseFilterExerciseTitle = resultWithOnlyCoursesFilterActive.getResultsOnPage().getFirst().getTitle();
        assertThat(courseFilterExerciseTitle).isEqualTo(searchTerm);

        // only exam filter set -> should show only the exercise course
        final MultiValueMap<String, String> examFilterParams = pageableSearchUtilService.searchMapping(search);
        examFilterParams.add("isCourseFilter", "false");
        examFilterParams.add("isExamFilter", "true");
        final var resultWithOnlyExamFilterActive = request.getSearchResult(apiPath, HttpStatus.OK, Exercise.class, examFilterParams);
        assertThat(resultWithOnlyExamFilterActive.getResultsOnPage()).hasSize(1);
        String examFilterExerciseTitle = resultWithOnlyExamFilterActive.getResultsOnPage().getFirst().getTitle();
        assertThat(examFilterExerciseTitle).isEqualTo(searchTerm + "-Morpork");

        var columnNameMap = PageUtil.ColumnMapping.EXERCISE.getColumnNameMap();

        for (var sort : columnNameMap.keySet()) {
            if (sort.equals("PROGRAMMING_LANGUAGE") && !apiPath.contains("programming")) {
                continue;
            }
            for (var order : List.of(SortingOrder.ASCENDING, SortingOrder.DESCENDING)) {
                search = pageableSearchUtilService.configureSearch(searchTerm);
                search.setSortedColumn(sort);
                search.setSortingOrder(order);
                var params = pageableSearchUtilService.searchMapping(search);

                if (sort.equals("EXAM_TITLE")) {
                    params.add("isCourseFilter", "false");
                }
                else if (sort.equals("COURSE_TITLE")) {
                    params.add("isExamFilter", "false");
                }

                var result = request.getSearchResult(apiPath, HttpStatus.OK, Exercise.class, params);

                var exerciseComparator = getExpectedComparator(sort);
                if (order == SortingOrder.DESCENDING) {
                    exerciseComparator = exerciseComparator.reversed();
                }
                assertThat(result.getResultsOnPage()).as("Sorting by " + sort + " " + order).isSortedAccordingTo(exerciseComparator);
            }
        }
    }

    private Comparator<Exercise> getExpectedComparator(String sort) {
        return switch (sort) {
            case "ID" -> Comparator.comparing(exercise -> exercise.getId());
            case "TITLE" -> Comparator.comparing(exercise -> exercise.getTitle());
            case "COURSE_TITLE" -> Comparator.comparing(exercise -> exercise.getCourseViaExerciseGroupOrCourseMember().getTitle());
            case "EXAM_TITLE" -> Comparator.comparing(exercise -> exercise.getExerciseGroup().getExam().getTitle());
            case "PROGRAMMING_LANGUAGE" -> Comparator.comparing(exercise -> ((ProgrammingExercise) exercise).getProgrammingLanguage());
            default -> throw new IllegalStateException("Unexpected value: " + sort);
        };
    }

}
