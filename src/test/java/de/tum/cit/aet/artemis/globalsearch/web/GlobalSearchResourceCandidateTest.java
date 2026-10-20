package de.tum.cit.aet.artemis.globalsearch.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.dto.GlobalSearchResultDTO;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityAccessFilterService;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityWeaviateService;

/**
 * How many candidates the search reads from Weaviate before ranking: browsing reads only the limit, and a query reads more when merging
 * semester copies shrank a cut-off candidate set below the requested limit.
 */
class GlobalSearchResourceCandidateTest {

    private SearchableEntityWeaviateService weaviateService;

    private GlobalSearchResource resource;

    @BeforeEach
    void setUp() {
        weaviateService = mock(SearchableEntityWeaviateService.class);
        var accessFilterService = mock(SearchableEntityAccessFilterService.class);
        var userRepository = mock(UserRepository.class);
        when(userRepository.getUserWithCourseRolesAndAuthorities()).thenReturn(new User());
        var courses = Map.of(1L, course(1L, "Algorithms WS26/27", 3), 2L, course(2L, "Algorithms SS26", -3), 3L, course(3L, "Algorithms WS25/26", -9), 4L,
                course(4L, "Algorithms SS25", -15), 5L, course(5L, "Algorithms WS24/25", -21));
        when(accessFilterService.buildSearchableItemFilter(any(), isNull(), anyList(), anySet(), anySet(), anyBoolean()))
                .thenReturn(new SearchableEntityAccessFilterService.FilterBuildResult(null, true, courses, Set.of(), Set.of()));
        resource = new GlobalSearchResource(weaviateService, accessFilterService, mock(CourseRepository.class), userRepository, mock(ExerciseRepository.class));
    }

    @Test
    void browsingReadsOnlyTheRequestedLimit() {
        when(weaviateService.searchSearchableEntities(any(), any(), anyInt())).thenReturn(List.of());

        resource.globalSearch("", null, null, null, null, 10, null);

        verify(weaviateService).searchSearchableEntities(eq(""), any(), eq(10));
    }

    @Test
    void readsMoreCandidatesWhenMergingSemesterCopiesShrankACutOffList() {
        // 40 candidates (the cut-off for a limit of 10) that are 8 exercises repeated in 5 course runs merge into 8 results.
        List<Map<String, Object>> copies = new ArrayList<>();
        for (int run = 0; run < 5; run++) {
            for (int exercise = 0; exercise < 8; exercise++) {
                copies.add(row(1L + run, "Exercise " + exercise));
            }
        }
        when(weaviateService.searchSearchableEntities(eq("sorting"), any(), eq(40))).thenReturn(copies);
        when(weaviateService.searchSearchableEntities(eq("sorting"), any(), eq(100))).thenReturn(copies);

        resource.globalSearch("sorting", null, null, null, null, 10, null);

        verify(weaviateService).searchSearchableEntities(eq("sorting"), any(), eq(100));
    }

    @Test
    void doesNotReadMoreWhenTheCandidateSetWasNotCutOff() {
        when(weaviateService.searchSearchableEntities(eq("sorting"), any(), eq(40))).thenReturn(List.of(row(1L, "Sorting")));

        var response = resource.globalSearch("sorting", null, null, null, null, 10, null);

        assertThat(response.getBody()).hasSize(1);
        verify(weaviateService, org.mockito.Mockito.never()).searchSearchableEntities(eq("sorting"), any(), eq(100));
    }

    @Test
    void returnsOnlyTheChannelsWhoseNameContainsTheQuery() {
        var lecture = row(1L, "Graph Algorithms");
        var unnamedChannel = channelRow(1L, 11L, "lecture-graph-algorithms");
        var namedChannel = channelRow(1L, 12L, "dijkstra-questions");
        when(weaviateService.searchSearchableEntities(eq("dijkstra"), any(), eq(40))).thenReturn(List.of(lecture, unnamedChannel, namedChannel));

        var response = resource.globalSearch("dijkstra", null, null, null, null, 10, null);

        assertThat(response.getBody()).extracting(GlobalSearchResultDTO::title).containsExactly("Graph Algorithms", "dijkstra-questions");
    }

    @Test
    void readsMoreCandidatesWhenDroppingChannelsShrankACutOffList() {
        List<Map<String, Object>> unnamedChannels = new ArrayList<>();
        for (int channel = 0; channel < 40; channel++) {
            unnamedChannels.add(channelRow(1L, channel, "lecture-graphs-" + channel));
        }
        List<Map<String, Object>> moreRows = new ArrayList<>(unnamedChannels);
        moreRows.add(row(1L, "Dijkstra"));
        moreRows.add(channelRow(1L, 99L, "lecture-dijkstra"));
        when(weaviateService.searchSearchableEntities(eq("dijkstra"), any(), eq(40))).thenReturn(unnamedChannels);
        when(weaviateService.searchSearchableEntities(eq("dijkstra"), any(), eq(100))).thenReturn(moreRows);

        var response = resource.globalSearch("dijkstra", null, null, null, null, 10, null);

        verify(weaviateService).searchSearchableEntities(eq("dijkstra"), any(), eq(100));
        assertThat(response.getBody()).extracting(GlobalSearchResultDTO::title).containsExactly("Dijkstra", "lecture-dijkstra");
    }

    private static Course course(long id, String title, int endInMonths) {
        var course = new Course();
        course.setId(id);
        course.setTitle(title);
        course.setStartDate(ZonedDateTime.now().plusMonths(endInMonths - 6));
        course.setEndDate(ZonedDateTime.now().plusMonths(endInMonths));
        return course;
    }

    private static Map<String, Object> row(long courseId, String title) {
        return Map.of(SearchableEntitySchema.Properties.TYPE, SearchableEntitySchema.TypeValues.LECTURE, SearchableEntitySchema.Properties.ENTITY_ID, (long) title.hashCode(),
                SearchableEntitySchema.Properties.COURSE_ID, courseId, SearchableEntitySchema.Properties.TITLE, title);
    }

    private static Map<String, Object> channelRow(long courseId, long channelId, String name) {
        return Map.of(SearchableEntitySchema.Properties.TYPE, SearchableEntitySchema.TypeValues.CHANNEL, SearchableEntitySchema.Properties.ENTITY_ID, channelId,
                SearchableEntitySchema.Properties.COURSE_ID, courseId, SearchableEntitySchema.Properties.TITLE, name);
    }
}
