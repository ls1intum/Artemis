package de.tum.in.www1.artemis.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;

import de.tum.in.www1.artemis.domain.Exercise;

/**
 * Service responsible for util methods / shared code that is helpful or common for testing all exercise types
 */
@Service
public class ExerciseIntegrationTestUtils {

    @Autowired
    protected DatabaseUtilService database;

    @Autowired
    protected RequestUtilService request;

    public void testCourseAndExamFilters(String apiPath, String searchTerm, Class<? extends Exercise> searchType) throws Exception {
        final var search = database.configureSearch(searchTerm);

        // no filter explicitly set -> should default to all filters active and show both exercises
        final var resultWithoutFiltersSet = request.getSearchResult(apiPath, HttpStatus.OK, searchType, database.searchMapping(search));
        assertThat(resultWithoutFiltersSet.getResultsOnPage()).hasSize(2);

        // both filter explicitly set -> should show both exercises
        final MultiValueMap<String, String> courseAndExamFilterParams = database.searchMapping(search);
        courseAndExamFilterParams.add("isCourseFilter", "true");
        courseAndExamFilterParams.add("isExamFilter", "true");
        final var resultWithCourseAndExamFiltersActive = request.getSearchResult(apiPath, HttpStatus.OK, searchType, courseAndExamFilterParams);
        assertThat(resultWithCourseAndExamFiltersActive.getResultsOnPage()).hasSize(2);

        // both filter explicitly deactivated -> should show no exercises
        final MultiValueMap<String, String> allFiltersInactiveParams = database.searchMapping(search);
        allFiltersInactiveParams.add("isCourseFilter", "false");
        allFiltersInactiveParams.add("isExamFilter", "false");
        final var resultWithNoFiltersActive = request.getSearchResult(apiPath, HttpStatus.OK, searchType, allFiltersInactiveParams);
        assertThat(resultWithNoFiltersActive.getResultsOnPage()).hasSize(0);

        // only course filter set -> should show only the exercise course
        final MultiValueMap<String, String> courseFilterParams = database.searchMapping(search);
        courseFilterParams.add("isCourseFilter", "true");
        courseFilterParams.add("isExamFilter", "false");
        final var resultWithOnlyCoursesFilterActive = request.getSearchResult(apiPath, HttpStatus.OK, searchType, courseFilterParams);
        assertThat(resultWithOnlyCoursesFilterActive.getResultsOnPage()).hasSize(1);
        String courseFilterExerciseTitle = resultWithOnlyCoursesFilterActive.getResultsOnPage().get(0).getTitle();
        assertThat(courseFilterExerciseTitle).isEqualTo(searchTerm);

        // only exam filter set -> should show only the exercise course
        final MultiValueMap<String, String> examFilterParams = database.searchMapping(search);
        examFilterParams.add("isCourseFilter", "false");
        examFilterParams.add("isExamFilter", "true");
        final var resultWithOnlyExamFilterActive = request.getSearchResult(apiPath, HttpStatus.OK, searchType, examFilterParams);
        assertThat(resultWithOnlyExamFilterActive.getResultsOnPage()).hasSize(1);
        String examFilterExerciseTitle = resultWithOnlyExamFilterActive.getResultsOnPage().get(0).getTitle();
        assertThat(examFilterExerciseTitle).isEqualTo(searchTerm + "-Morpork");
    }

}
