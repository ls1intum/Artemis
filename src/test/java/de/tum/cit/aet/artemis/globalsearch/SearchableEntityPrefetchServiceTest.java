package de.tum.cit.aet.artemis.globalsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.dto.SearchableEntityCandidateDTO;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityAccessFilterService;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityPrefetchService;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityWeaviateService;

class SearchableEntityPrefetchServiceTest {

    private SearchableEntityAccessFilterService accessFilterService;

    private SearchableEntityWeaviateService weaviateService;

    private SearchableEntityPrefetchService prefetchService;

    @BeforeEach
    void setUp() {
        accessFilterService = mock(SearchableEntityAccessFilterService.class);
        weaviateService = mock(SearchableEntityWeaviateService.class);
        prefetchService = new SearchableEntityPrefetchService(accessFilterService, weaviateService, mock(CourseRepository.class));
    }

    private void givenAccessibleRows(List<Map<String, Object>> rows) {
        var course = new de.tum.cit.aet.artemis.course.domain.Course();
        course.setId(9L);
        course.setTitle("Patterns in Software Engineering");
        when(accessFilterService.buildSearchableItemFilter(any(), any(), anySet()))
                .thenReturn(new SearchableEntityAccessFilterService.FilterBuildResult(null, true, Map.of(9L, course), java.util.Set.of(), java.util.Set.of()));
        when(weaviateService.searchSearchableEntities(any(), any(), anyInt())).thenReturn(rows);
    }

    @Test
    void mapsAnExerciseRowWithDatesPointsAndLink() {
        Map<String, Object> row = new HashMap<>();
        row.put(SearchableEntitySchema.Properties.TYPE, "exercise");
        row.put(SearchableEntitySchema.Properties.ENTITY_ID, 42L);
        row.put(SearchableEntitySchema.Properties.COURSE_ID, 9L);
        row.put(SearchableEntitySchema.Properties.TITLE, "W03E03 Flyweight Pattern");
        row.put(SearchableEntitySchema.Properties.EXERCISE_TYPE, "programming");
        row.put(SearchableEntitySchema.Properties.MAX_POINTS, 10.0);
        row.put(SearchableEntitySchema.Properties.QUIZ_DURATION, 600L);
        row.put(SearchableEntitySchema.Properties.DUE_DATE, OffsetDateTime.of(2026, 5, 17, 18, 24, 0, 0, ZoneOffset.UTC));
        givenAccessibleRows(List.of(row));

        List<SearchableEntityCandidateDTO> candidates = prefetchService.prefetchCandidates(new User(), "flyweight", 10);

        assertThat(candidates).hasSize(1);
        SearchableEntityCandidateDTO candidate = candidates.getFirst();
        assertThat(candidate.entityType()).isEqualTo("exercise");
        assertThat(candidate.courseName()).isEqualTo("Patterns in Software Engineering");
        assertThat(candidate.dueDate()).startsWith("2026-05-17T18:24");
        assertThat(candidate.maxPoints()).isEqualTo(10.0);
        assertThat(candidate.quizDurationSeconds()).isEqualTo(600L);
        assertThat(candidate.link()).isEqualTo("/courses/9/exercises/42");
    }

    @Test
    void buildsTypeSpecificLinks() {
        Map<String, Object> unit = new HashMap<>(Map.of(SearchableEntitySchema.Properties.TYPE, "lecture_unit", SearchableEntitySchema.Properties.ENTITY_ID, 5L,
                SearchableEntitySchema.Properties.COURSE_ID, 9L, SearchableEntitySchema.Properties.LECTURE_ID, 44L));
        Map<String, Object> channel = new HashMap<>(
                Map.of(SearchableEntitySchema.Properties.TYPE, "channel", SearchableEntitySchema.Properties.ENTITY_ID, 61L, SearchableEntitySchema.Properties.COURSE_ID, 9L));
        Map<String, Object> course = new HashMap<>(Map.of(SearchableEntitySchema.Properties.TYPE, "course", SearchableEntitySchema.Properties.ENTITY_ID, 9L));
        givenAccessibleRows(List.of(unit, channel, course));

        List<SearchableEntityCandidateDTO> candidates = prefetchService.prefetchCandidates(new User(), "q", 10);

        assertThat(candidates).extracting(SearchableEntityCandidateDTO::link).containsExactly("/courses/9/lectures/44", "/courses/9/communication?conversationId=61", "/courses/9");
    }

    @Test
    void returnsEmptyWithoutAccessibleCourses() {
        when(accessFilterService.buildSearchableItemFilter(any(), any(), anySet()))
                .thenReturn(new SearchableEntityAccessFilterService.FilterBuildResult(null, false, null, null, null));

        assertThat(prefetchService.prefetchCandidates(new User(), "q", 10)).isEmpty();
    }
}
