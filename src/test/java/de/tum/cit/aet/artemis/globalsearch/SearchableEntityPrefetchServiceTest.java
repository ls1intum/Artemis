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
        givenAccessibleRows(rows, java.util.Set.of(), java.util.Set.of());
    }

    private void givenAccessibleRows(List<Map<String, Object>> rows, java.util.Set<Long> editorCourseIds, java.util.Set<Long> staffCourseIds) {
        var course = new de.tum.cit.aet.artemis.course.domain.Course();
        course.setId(9L);
        course.setTitle("Patterns in Software Engineering");
        when(accessFilterService.buildSearchableItemFilter(any(), any(), any(), anySet()))
                .thenReturn(new SearchableEntityAccessFilterService.FilterBuildResult(null, true, Map.of(9L, course), staffCourseIds, editorCourseIds));
        when(weaviateService.searchEntityCandidatesForAnswer(any(), any(), anyInt())).thenReturn(rows);
    }

    private static Map<String, Object> examRow() {
        return new HashMap<>(
                Map.of(SearchableEntitySchema.Properties.TYPE, "exam", SearchableEntitySchema.Properties.ENTITY_ID, 30L, SearchableEntitySchema.Properties.COURSE_ID, 9L));
    }

    private static Map<String, Object> examExerciseRow() {
        return new HashMap<>(Map.of(SearchableEntitySchema.Properties.TYPE, "exercise", SearchableEntitySchema.Properties.ENTITY_ID, 42L,
                SearchableEntitySchema.Properties.COURSE_ID, 9L, SearchableEntitySchema.Properties.EXAM_ID, 30L));
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

        List<SearchableEntityCandidateDTO> candidates = prefetchService.prefetchCandidates(new User(), "flyweight", 10, null, List.of());

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

        List<SearchableEntityCandidateDTO> candidates = prefetchService.prefetchCandidates(new User(), "q", 10, null, List.of());

        assertThat(candidates).extracting(SearchableEntityCandidateDTO::link).containsExactly("/courses/9/lectures/44", "/courses/9/communication?conversationId=61", "/courses/9");
    }

    @Test
    void buildsFocusedLinksForPostsAndReplies() {
        // Mirrors the palette's own click handler (GlobalSearchNavigationViewComponent), which opens a
        // post via focusPostId and a reply via messageId (the parent post) + focusReplyId. Without these
        // query params the citation opens the channel but not the specific cited message.
        Map<String, Object> post = new HashMap<>(Map.of(SearchableEntitySchema.Properties.TYPE, "post", SearchableEntitySchema.Properties.ENTITY_ID, 77L,
                SearchableEntitySchema.Properties.COURSE_ID, 9L, SearchableEntitySchema.Properties.CHANNEL_ID, 61L));
        Map<String, Object> answerPost = new HashMap<>(Map.of(SearchableEntitySchema.Properties.TYPE, "answer_post", SearchableEntitySchema.Properties.ENTITY_ID, 88L,
                SearchableEntitySchema.Properties.COURSE_ID, 9L, SearchableEntitySchema.Properties.CHANNEL_ID, 61L, SearchableEntitySchema.Properties.POST_ID, 77L));
        givenAccessibleRows(List.of(post, answerPost));

        List<SearchableEntityCandidateDTO> candidates = prefetchService.prefetchCandidates(new User(), "q", 10, null, List.of());

        assertThat(candidates).extracting(SearchableEntityCandidateDTO::link).containsExactly("/courses/9/communication?conversationId=61&focusPostId=77",
                "/courses/9/communication?conversationId=61&messageId=77&focusReplyId=88");
    }

    @Test
    void editorGetsExamManagementAndExerciseGroupsLinks() {
        // Mirrors GlobalSearchNavigationViewComponent.navigateToExam/navigateToExercise: an editor
        // manages the exam and its exercise groups, not the student-facing routes.
        givenAccessibleRows(List.of(examRow(), examExerciseRow()), java.util.Set.of(9L), java.util.Set.of(9L));

        List<SearchableEntityCandidateDTO> candidates = prefetchService.prefetchCandidates(new User(), "q", 10, null, List.of());

        assertThat(candidates).extracting(SearchableEntityCandidateDTO::link).containsExactly("/course-management/9/exams/30", "/course-management/9/exams/30/exercise-groups");
    }

    @Test
    void teachingAssistantGetsAssessmentDashboardLinks() {
        // A TA (staff but not editor) assesses; the exam-exercise link is scoped to the specific exercise,
        // the exam link is not (there is no single "assess this exam" exercise to jump to).
        givenAccessibleRows(List.of(examRow(), examExerciseRow()), java.util.Set.of(), java.util.Set.of(9L));

        List<SearchableEntityCandidateDTO> candidates = prefetchService.prefetchCandidates(new User(), "q", 10, null, List.of());

        assertThat(candidates).extracting(SearchableEntityCandidateDTO::link).containsExactly("/course-management/9/exams/30/assessment-dashboard",
                "/course-management/9/exams/30/assessment-dashboard/42");
    }

    @Test
    void studentGetsTheExamViewForBothTheExamAndItsExercises() {
        // A student citing an exam exercise lands on the exam overview, not a specific exercise route —
        // mirroring navigateToExercise's student branch, which has no standalone exam-exercise page.
        givenAccessibleRows(List.of(examRow(), examExerciseRow()), java.util.Set.of(), java.util.Set.of());

        List<SearchableEntityCandidateDTO> candidates = prefetchService.prefetchCandidates(new User(), "q", 10, null, List.of());

        assertThat(candidates).extracting(SearchableEntityCandidateDTO::link).containsExactly("/courses/9/exams/30", "/courses/9/exams/30");
    }

    @Test
    void examRowReadsItsOwnVisibleDateNotTheExerciseDenormalizedOne() {
        // EXAM_VISIBLE_DATE is denormalized onto exam-exercise rows only; an exam row carries its own
        // visibility in VISIBLE_DATE. Reading EXAM_VISIBLE_DATE for an exam row (as if it were an
        // exercise) would silently send Pyris a null visibleDate even though the value is indexed.
        Map<String, Object> exam = examRow();
        exam.put(SearchableEntitySchema.Properties.VISIBLE_DATE, OffsetDateTime.of(2026, 5, 10, 8, 0, 0, 0, ZoneOffset.UTC));
        Map<String, Object> examExercise = examExerciseRow();
        examExercise.put(SearchableEntitySchema.Properties.EXAM_VISIBLE_DATE, OffsetDateTime.of(2026, 5, 10, 8, 0, 0, 0, ZoneOffset.UTC));
        givenAccessibleRows(List.of(exam, examExercise));

        List<SearchableEntityCandidateDTO> candidates = prefetchService.prefetchCandidates(new User(), "q", 10, null, List.of());

        assertThat(candidates).extracting(SearchableEntityCandidateDTO::visibleDate).allSatisfy(date -> assertThat(date).startsWith("2026-05-10T08:00"));
    }

    @Test
    void returnsEmptyWithoutAccessibleCourses() {
        when(accessFilterService.buildSearchableItemFilter(any(), any(), any(), anySet()))
                .thenReturn(new SearchableEntityAccessFilterService.FilterBuildResult(null, false, null, null, null));

        assertThat(prefetchService.prefetchCandidates(new User(), "q", 10, null, List.of())).isEmpty();
    }
}
