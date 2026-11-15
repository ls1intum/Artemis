package de.tum.cit.aet.artemis.globalsearch.service.reconcile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.domain.Pageable;

import de.tum.cit.aet.artemis.exam.api.ExamRepositoryApi;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.repository.SearchableEntityIdRepository;
import de.tum.cit.aet.artemis.lecture.api.LectureRepositoryApi;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitRepositoryApi;

class SearchableEntityIdEnumeratorTest {

    private static final long AFTER_ID = 41L;

    private static final int PAGE_SIZE = 17;

    private final SearchableEntityIdRepository idRepository = mock(SearchableEntityIdRepository.class);

    private final LectureRepositoryApi lectureRepositoryApi = mock(LectureRepositoryApi.class);

    private final LectureUnitRepositoryApi lectureUnitRepositoryApi = mock(LectureUnitRepositoryApi.class);

    private final ExamRepositoryApi examRepositoryApi = mock(ExamRepositoryApi.class);

    private final SearchableEntityIdEnumerator enumerator = new SearchableEntityIdEnumerator(idRepository, Optional.of(lectureRepositoryApi), Optional.of(lectureUnitRepositoryApi),
            Optional.of(examRepositoryApi));

    private static Stream<String> searchableEntityTypes() {
        return Stream.of(SearchableEntitySchema.TypeValues.COURSE, SearchableEntitySchema.TypeValues.EXERCISE, SearchableEntitySchema.TypeValues.FAQ,
                SearchableEntitySchema.TypeValues.CHANNEL, SearchableEntitySchema.TypeValues.POST, SearchableEntitySchema.TypeValues.ANSWER_POST,
                SearchableEntitySchema.TypeValues.LECTURE, SearchableEntitySchema.TypeValues.LECTURE_UNIT, SearchableEntitySchema.TypeValues.EXAM);
    }

    @ParameterizedTest
    @MethodSource("searchableEntityTypes")
    void testNextIndexableIds_routesEveryTypeAndPreservesCursorAndPageSize(String entityType) {
        List<Long> expectedIds = List.of((long) entityType.hashCode());
        stubNextPage(entityType, expectedIds);

        assertThat(enumerator.nextIndexableIds(entityType, AFTER_ID, PAGE_SIZE)).contains(expectedIds);

        verifyNextPageRoute(entityType);
    }

    @ParameterizedTest
    @MethodSource("searchableEntityTypes")
    void testIndexableIdsAmong_routesEveryTypeToItsIndexabilityQuery(String entityType) {
        Collection<Long> entityIds = List.of(4L, 8L, 15L);
        Set<Long> expectedIds = Set.of(8L);
        stubIndexabilityQuery(entityType, expectedIds);

        assertThat(enumerator.indexableIdsAmong(entityType, entityIds)).contains(expectedIds);

        verifyIndexabilityRoute(entityType, entityIds);
    }

    @ParameterizedTest
    @MethodSource("optionalModuleTypes")
    void testDisabledOptionalModuleRemainsUnknownInsteadOfEmpty(String entityType) {
        SearchableEntityIdEnumerator withoutOptionalModules = new SearchableEntityIdEnumerator(idRepository, Optional.empty(), Optional.empty(), Optional.empty());

        assertThat(withoutOptionalModules.isTypeAvailable(entityType)).isFalse();
        assertThat(withoutOptionalModules.nextIndexableIds(entityType, AFTER_ID, PAGE_SIZE)).isEmpty();
        assertThat(withoutOptionalModules.indexableIdsAmong(entityType, List.of(4L))).isEmpty();
        verifyNoInteractions(idRepository, lectureRepositoryApi, lectureUnitRepositoryApi, examRepositoryApi);
    }

    @Test
    void testUnknownTypeIsRejectedByEnumerationOperations() {
        assertThatThrownBy(() -> enumerator.nextIndexableIds("unknown", AFTER_ID, PAGE_SIZE)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unknown searchable entity type");
        assertThatThrownBy(() -> enumerator.indexableIdsAmong("unknown", List.of(1L))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unknown searchable entity type");
    }

    @ParameterizedTest
    @MethodSource("emptyCollectionTypes")
    void testEmptyIndexabilityLookupDoesNotQueryAnyRepository(String entityType) {
        assertThat(enumerator.indexableIdsAmong(entityType, List.of())).contains(Set.of());

        verifyNoInteractions(idRepository, lectureRepositoryApi, lectureUnitRepositoryApi, examRepositoryApi);
    }

    private static Stream<String> optionalModuleTypes() {
        return Stream.of(SearchableEntitySchema.TypeValues.LECTURE, SearchableEntitySchema.TypeValues.LECTURE_UNIT, SearchableEntitySchema.TypeValues.EXAM);
    }

    private static Stream<String> emptyCollectionTypes() {
        return Stream.of(SearchableEntitySchema.TypeValues.COURSE, SearchableEntitySchema.TypeValues.LECTURE, SearchableEntitySchema.TypeValues.EXAM);
    }

    private void stubNextPage(String entityType, List<Long> ids) {
        switch (entityType) {
            case SearchableEntitySchema.TypeValues.COURSE -> when(idRepository.findCourseIdsAfter(eq(AFTER_ID), any(Pageable.class))).thenReturn(ids);
            case SearchableEntitySchema.TypeValues.EXERCISE -> when(idRepository.findExerciseIdsAfter(eq(AFTER_ID), any(Pageable.class))).thenReturn(ids);
            case SearchableEntitySchema.TypeValues.FAQ -> when(idRepository.findFaqIdsAfter(eq(AFTER_ID), any(Pageable.class))).thenReturn(ids);
            case SearchableEntitySchema.TypeValues.CHANNEL -> when(idRepository.findIndexableChannelIdsAfter(eq(AFTER_ID), any(Pageable.class))).thenReturn(ids);
            case SearchableEntitySchema.TypeValues.POST -> when(idRepository.findIndexablePostIdsAfter(eq(AFTER_ID), any(Pageable.class))).thenReturn(ids);
            case SearchableEntitySchema.TypeValues.ANSWER_POST -> when(idRepository.findIndexableAnswerPostIdsAfter(eq(AFTER_ID), any(Pageable.class))).thenReturn(ids);
            case SearchableEntitySchema.TypeValues.LECTURE -> when(lectureRepositoryApi.findLectureIdsAfter(AFTER_ID, PAGE_SIZE)).thenReturn(ids);
            case SearchableEntitySchema.TypeValues.LECTURE_UNIT -> when(lectureUnitRepositoryApi.findIndexableUnitIdsAfter(AFTER_ID, PAGE_SIZE)).thenReturn(ids);
            case SearchableEntitySchema.TypeValues.EXAM -> when(examRepositoryApi.findExamIdsAfter(AFTER_ID, PAGE_SIZE)).thenReturn(ids);
            default -> throw new IllegalArgumentException("Unsupported test type: " + entityType);
        }
    }

    private void verifyNextPageRoute(String entityType) {
        switch (entityType) {
            case SearchableEntitySchema.TypeValues.COURSE -> verify(idRepository).findCourseIdsAfter(eq(AFTER_ID), pageOfSize(PAGE_SIZE));
            case SearchableEntitySchema.TypeValues.EXERCISE -> verify(idRepository).findExerciseIdsAfter(eq(AFTER_ID), pageOfSize(PAGE_SIZE));
            case SearchableEntitySchema.TypeValues.FAQ -> verify(idRepository).findFaqIdsAfter(eq(AFTER_ID), pageOfSize(PAGE_SIZE));
            case SearchableEntitySchema.TypeValues.CHANNEL -> verify(idRepository).findIndexableChannelIdsAfter(eq(AFTER_ID), pageOfSize(PAGE_SIZE));
            case SearchableEntitySchema.TypeValues.POST -> verify(idRepository).findIndexablePostIdsAfter(eq(AFTER_ID), pageOfSize(PAGE_SIZE));
            case SearchableEntitySchema.TypeValues.ANSWER_POST -> verify(idRepository).findIndexableAnswerPostIdsAfter(eq(AFTER_ID), pageOfSize(PAGE_SIZE));
            case SearchableEntitySchema.TypeValues.LECTURE -> verify(lectureRepositoryApi).findLectureIdsAfter(AFTER_ID, PAGE_SIZE);
            case SearchableEntitySchema.TypeValues.LECTURE_UNIT -> verify(lectureUnitRepositoryApi).findIndexableUnitIdsAfter(AFTER_ID, PAGE_SIZE);
            case SearchableEntitySchema.TypeValues.EXAM -> verify(examRepositoryApi).findExamIdsAfter(AFTER_ID, PAGE_SIZE);
            default -> throw new IllegalArgumentException("Unsupported test type: " + entityType);
        }
    }

    private void stubIndexabilityQuery(String entityType, Set<Long> ids) {
        switch (entityType) {
            case SearchableEntitySchema.TypeValues.COURSE -> when(idRepository.findExistingCourseIds(any())).thenReturn(ids);
            case SearchableEntitySchema.TypeValues.EXERCISE -> when(idRepository.findExistingExerciseIds(any())).thenReturn(ids);
            case SearchableEntitySchema.TypeValues.FAQ -> when(idRepository.findExistingFaqIds(any())).thenReturn(ids);
            case SearchableEntitySchema.TypeValues.CHANNEL -> when(idRepository.findIndexableChannelIds(any())).thenReturn(ids);
            case SearchableEntitySchema.TypeValues.POST -> when(idRepository.findIndexablePostIds(any())).thenReturn(ids);
            case SearchableEntitySchema.TypeValues.ANSWER_POST -> when(idRepository.findIndexableAnswerPostIds(any())).thenReturn(ids);
            case SearchableEntitySchema.TypeValues.LECTURE -> when(lectureRepositoryApi.findExistingLectureIds(any())).thenReturn(ids);
            case SearchableEntitySchema.TypeValues.LECTURE_UNIT -> when(lectureUnitRepositoryApi.findIndexableUnitIds(any())).thenReturn(ids);
            case SearchableEntitySchema.TypeValues.EXAM -> when(examRepositoryApi.findExistingExamIds(any())).thenReturn(ids);
            default -> throw new IllegalArgumentException("Unsupported test type: " + entityType);
        }
    }

    private void verifyIndexabilityRoute(String entityType, Collection<Long> entityIds) {
        switch (entityType) {
            case SearchableEntitySchema.TypeValues.COURSE -> verify(idRepository).findExistingCourseIds(entityIds);
            case SearchableEntitySchema.TypeValues.EXERCISE -> verify(idRepository).findExistingExerciseIds(entityIds);
            case SearchableEntitySchema.TypeValues.FAQ -> verify(idRepository).findExistingFaqIds(entityIds);
            case SearchableEntitySchema.TypeValues.CHANNEL -> verify(idRepository).findIndexableChannelIds(entityIds);
            case SearchableEntitySchema.TypeValues.POST -> verify(idRepository).findIndexablePostIds(entityIds);
            case SearchableEntitySchema.TypeValues.ANSWER_POST -> verify(idRepository).findIndexableAnswerPostIds(entityIds);
            case SearchableEntitySchema.TypeValues.LECTURE -> verify(lectureRepositoryApi).findExistingLectureIds(entityIds);
            case SearchableEntitySchema.TypeValues.LECTURE_UNIT -> verify(lectureUnitRepositoryApi).findIndexableUnitIds(entityIds);
            case SearchableEntitySchema.TypeValues.EXAM -> verify(examRepositoryApi).findExistingExamIds(entityIds);
            default -> throw new IllegalArgumentException("Unsupported test type: " + entityType);
        }
    }

    private static Pageable pageOfSize(int pageSize) {
        return argThat(pageable -> pageable.getPageNumber() == 0 && pageable.getPageSize() == pageSize);
    }
}
