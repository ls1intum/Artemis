package de.tum.in.www1.artemis.util;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

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
     * Generates a PageableSearchDTO for FinishedJobs.
     *
     * @return The generated PageableSearchDTO
     */
    public PageableSearchDTO<String> configureFinishedJobsSearchDTO() {
        final var search = new PageableSearchDTO<String>();
        search.setPage(1);
        search.setPageSize(10);
        search.setSortedColumn("build_completion_date");
        search.setSortingOrder(SortingOrder.DESCENDING);
        return search;
    }

    /**
     * Generates a LinkedMultiValueMap from the given PageableSearchDTO. The map is used for REST calls and maps the parameters to the values.
     * Converts a PageableSearchDTO into a LinkedMultiValueMap suitable for use with RESTful API calls.
     * This conversion facilitates the transfer of search parameters and their values in a format
     * that is acceptable for web requests.
     *
     * @param search The PageableSearchDTO containing search parameters and values
     * @return A LinkedMultiValueMap with parameter names as keys and their corresponding values
     */
    public LinkedMultiValueMap<String, String> searchMapping(PageableSearchDTO<String> search) {
        final ObjectMapper objectMapper = new ObjectMapper();
        try {
            // Serialize the DTO into a JSON string and then deserialize it into a Map
            final String json = objectMapper.writeValueAsString(search);
            final Map<String, String> params = objectMapper.readValue(json, new TypeReference<>() {
            });

            // Populate a LinkedMultiValueMap from the Map, mapping parameter names to values
            final LinkedMultiValueMap<String, String> paramMap = new LinkedMultiValueMap<>();
            params.forEach(paramMap::add);
            return paramMap;
        }
        catch (Exception e) {
            // Handle exceptions by throwing a runtime exception with the error context
            throw new RuntimeException("Failed to map JSON", e);
        }
    }
}
