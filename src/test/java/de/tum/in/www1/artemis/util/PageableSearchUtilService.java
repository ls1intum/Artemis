package de.tum.in.www1.artemis.util;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import de.tum.in.www1.artemis.domain.enumeration.SortingOrder;
import de.tum.in.www1.artemis.web.rest.dto.pageablesearch.CompetencyPageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.dto.pageablesearch.PageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.dto.pageablesearch.SearchTermPageableSearchDTO;

/**
 * Service responsible for initializing the database with specific testdata related to searches for use in integration tests.
 */
@Service
public class PageableSearchUtilService {

    /**
     * Generates a PageableSearchDTO for Exercises.
     *
     * @param searchTerm The searchTerm to use
     * @return The generated PageableSearchDTO
     */
    public SearchTermPageableSearchDTO<String> configureSearch(String searchTerm) {
        final var search = new SearchTermPageableSearchDTO<String>();
        search.setPage(1);
        search.setPageSize(10);
        search.setSearchTerm(searchTerm);
        search.setSortedColumn("ID");
        if ("".equals(searchTerm)) {
            search.setSortingOrder(SortingOrder.ASCENDING);
        }
        else {
            search.setSortingOrder(SortingOrder.DESCENDING);
        }
        return search;
    }

    /**
     * Generates a PageableSearchDTO for StudentParticipations.
     *
     * @param searchTerm The searchTerm to use
     * @return The generated PageableSearchDTO
     */
    public SearchTermPageableSearchDTO<String> configureStudentParticipationSearch(String searchTerm) {
        final var search = new SearchTermPageableSearchDTO<String>();
        search.setPage(1);
        search.setPageSize(10);
        search.setSearchTerm(searchTerm);
        search.setSortedColumn("ID");
        if ("".equals(searchTerm)) {
            search.setSortingOrder(SortingOrder.ASCENDING);
        }
        else {
            search.setSortingOrder(SortingOrder.DESCENDING);
        }
        return search;
    }

    /**
     * Generates a PageableSearchDTO for Lectures.
     *
     * @param searchTerm The searchTerm to use
     * @return The generated PageableSearchDTO
     */
    public SearchTermPageableSearchDTO<String> configureLectureSearch(String searchTerm) {
        final var search = new SearchTermPageableSearchDTO<String>();
        search.setPage(1);
        search.setPageSize(10);
        search.setSearchTerm(searchTerm);
        search.setSortedColumn("COURSE_TITLE");
        search.setSortingOrder(SortingOrder.DESCENDING);
        return search;
    }

    /**
     * Generates a CompetencyPageableSearchDTO with the given search terms
     *
     * @param title       the competency title
     * @param description the competency description
     * @param courseTitle the course title
     * @param semester    the course semester
     * @return The generated DTO
     */
    public CompetencyPageableSearchDTO configureCompetencySearch(String title, String description, String courseTitle, String semester) {
        final var search = new CompetencyPageableSearchDTO();
        search.setPage(1);
        search.setPageSize(10);
        search.setSortedColumn("ID");
        search.setSortingOrder(SortingOrder.DESCENDING);
        search.setTitle(title);
        search.setDescription(description);
        search.setCourseTitle(courseTitle);
        search.setSemester(semester);
        return search;
    }

    /**
     * Generates a LinkedMultiValueMap from the given PageableSearchDTO. The map is used for REST calls and maps the parameters to the values.
     *
     * @param search The PageableSearchDTO to use
     * @return The generated LinkedMultiValueMap
     */
    public LinkedMultiValueMap<String, String> searchMapping(PageableSearchDTO<String> search) {
        final var mapType = new TypeToken<Map<String, String>>() {
        }.getType();
        final var gson = new Gson();
        final Map<String, String> params = new Gson().fromJson(gson.toJson(search), mapType);
        final var paramMap = new LinkedMultiValueMap<String, String>();
        params.forEach(paramMap::add);
        return paramMap;
    }
}
