package de.tum.cit.aet.artemis.exercise.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import de.tum.cit.aet.artemis.core.dto.SortingOrder;

/**
 * Custom repository fragment for paginated, filtered participation queries.
 */
public interface CustomStudentParticipationRepository {

    /**
     * Returns a page of participation IDs for the given exercise, applying search, filter, and score-range predicates.
     *
     * @param exerciseId      the exercise to query
     * @param teamMode        whether the exercise uses teams
     * @param searchTerm      free-text search (matched against student login/name or team name/shortName)
     * @param filterProp      filter property name (All, Successful, Unsuccessful, BuildFailed, Manual, Automatic, Locked)
     * @param scoreRangeLower inclusive lower bound of score range filter (nullable)
     * @param scoreRangeUpper upper bound of score range filter; exclusive unless equal to 100 (nullable)
     * @param pageable        pagination information
     * @param sortOrder       ascending or descending
     * @param sortedColumn    the column to sort by
     * @return a page of participation IDs
     */
    Page<Long> findParticipationIdsByExerciseIdWithFilters(long exerciseId, boolean teamMode, String searchTerm, String filterProp, Integer scoreRangeLower,
            Integer scoreRangeUpper, Pageable pageable, SortingOrder sortOrder, String sortedColumn);
}
