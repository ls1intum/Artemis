package de.tum.in.www1.artemis.util;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.enumeration.SortingOrder;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.web.rest.dto.PageableSearchDTO;

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
    public PageableSearchDTO<String> configureSearch(String searchTerm) {
        final var search = new PageableSearchDTO<String>();
        search.setPage(1);
        search.setPageSize(10);
        search.setSearchTerm(searchTerm);
        search.setSortedColumn(Exercise.ExerciseSearchColumn.ID.name());
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
    public PageableSearchDTO<String> configureStudentParticipationSearch(String searchTerm) {
        final var search = new PageableSearchDTO<String>();
        search.setPage(1);
        search.setPageSize(10);
        search.setSearchTerm(searchTerm);
        search.setSortedColumn(StudentParticipation.StudentParticipationSearchColumn.ID.name());
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
    public PageableSearchDTO<String> configureLectureSearch(String searchTerm) {
        final var search = new PageableSearchDTO<String>();
        search.setPage(1);
        search.setPageSize(10);
        search.setSearchTerm(searchTerm);
        search.setSortedColumn(Lecture.LectureSearchColumn.COURSE_TITLE.name());
        search.setSortingOrder(SortingOrder.DESCENDING);
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
