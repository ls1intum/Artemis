package de.tum.cit.aet.artemis.globalsearch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.domain.WeaviateOutboxEntry;
import de.tum.cit.aet.artemis.globalsearch.domain.WeaviateOutboxOperation;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.AnswerPostSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.ChannelSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.CourseSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.ExamSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.ExerciseSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.FaqSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.LectureSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.LectureUnitSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.PostSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.repository.WeaviateOutboxRepository;

/**
 * Unit tests for the enqueue side of {@link SearchableEntityWeaviateService}.
 * <p>
 * Every public {@code upsert*Async} / {@code delete*Async} method must record the intent as a durable outbox
 * row (correct operation, type, id, and payload/params) and must NOT touch Weaviate synchronously — the
 * dispatcher performs the actual write later.
 */
class SearchableEntityWeaviateServiceEnqueueTest {

    private final WeaviateService weaviateService = mock(WeaviateService.class);

    private final WeaviateOutboxRepository outboxRepository = mock(WeaviateOutboxRepository.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    private SearchableEntityWeaviateService service;

    @BeforeEach
    void setUp() {
        service = new SearchableEntityWeaviateService(weaviateService, outboxRepository, objectMapper, eventPublisher);
    }

    @Test
    void testUpsertCourse_enqueuesRowWithCanonicalPayloadAndNoSynchronousWrite() throws Exception {
        var dto = new CourseSearchableEntityDTO(42L, "Algorithms", "ALG", "A course description");

        service.upsertCourseAsync(dto);

        WeaviateOutboxEntry entry = captureSavedEntry();
        assertThat(entry.getOperation()).isEqualTo(WeaviateOutboxOperation.UPSERT);
        assertThat(entry.getEntityType()).isEqualTo(SearchableEntitySchema.TypeValues.COURSE);
        assertThat(entry.getEntityId()).isEqualTo(42L);
        assertThat(entry.getParams()).isNull();
        // The stored payload is the canonical (key-sorted) serialization of the property map the call site built.
        assertThat(entry.getPayload()).isEqualTo(objectMapper.writeValueAsString(new TreeMap<>(dto.toPropertyMap())));
        // No write must reach Weaviate from the request path; the only Weaviate entry point is getCollection.
        verify(weaviateService, never()).getCollection(any());
        verify(eventPublisher).publishEvent(any(WeaviateOutboxEnqueuedEvent.class));
    }

    @Test
    void testEachUpsertMethod_enqueuesUpsertWithCorrectTypeAndId() {
        service.upsertCourseAsync(new CourseSearchableEntityDTO(1L, "c", null, null));
        service.upsertLectureAsync(new LectureSearchableEntityDTO(2L, 10L, "l", null, null, null));
        service.upsertLectureUnitAsync(new LectureUnitSearchableEntityDTO(3L, 10L, 20L, "u", null, "text", null));
        service.upsertExamAsync(new ExamSearchableEntityDTO(4L, 10L, "e", null, null, null, null, false));
        service.upsertFaqAsync(new FaqSearchableEntityDTO(5L, 10L, "q", "a", "ACCEPTED"));
        service.upsertChannelAsync(new ChannelSearchableEntityDTO(6L, 10L, "ch", null, false, true));
        service.upsertPostAsync(new PostSearchableEntityDTO(7L, 10L, 30L, "t", "content"));
        service.upsertAnswerPostAsync(new AnswerPostSearchableEntityDTO(8L, 7L, 10L, 30L, "reply"));
        service.upsertExerciseAsync(exerciseDto(9L));

        ArgumentCaptor<WeaviateOutboxEntry> captor = ArgumentCaptor.forClass(WeaviateOutboxEntry.class);
        verify(outboxRepository, times(9)).save(captor.capture());
        List<WeaviateOutboxEntry> saved = captor.getAllValues();

        assertThat(saved).allSatisfy(entry -> assertThat(entry.getOperation()).isEqualTo(WeaviateOutboxOperation.UPSERT));
        assertThat(saved).extracting(WeaviateOutboxEntry::getEntityType, WeaviateOutboxEntry::getEntityId).containsExactly(tuple(SearchableEntitySchema.TypeValues.COURSE, 1L),
                tuple(SearchableEntitySchema.TypeValues.LECTURE, 2L), tuple(SearchableEntitySchema.TypeValues.LECTURE_UNIT, 3L), tuple(SearchableEntitySchema.TypeValues.EXAM, 4L),
                tuple(SearchableEntitySchema.TypeValues.FAQ, 5L), tuple(SearchableEntitySchema.TypeValues.CHANNEL, 6L), tuple(SearchableEntitySchema.TypeValues.POST, 7L),
                tuple(SearchableEntitySchema.TypeValues.ANSWER_POST, 8L), tuple(SearchableEntitySchema.TypeValues.EXERCISE, 9L));
    }

    @Test
    void testUpdateExercises_enqueuesOneUpsertPerExerciseAndSignalsOnce() {
        service.updateExercisesAsync(List.of(exerciseDto(10L), exerciseDto(11L)), 99L);

        verify(outboxRepository, times(2)).save(any(WeaviateOutboxEntry.class));
        // A single batch signal is enough; the dispatcher drains until the outbox is empty.
        verify(eventPublisher, times(1)).publishEvent(any(WeaviateOutboxEnqueuedEvent.class));
        verify(weaviateService, never()).getCollection(any());
    }

    @Test
    void testDeleteEntity_enqueuesDeleteEntityRow() {
        service.deleteEntityAsync(SearchableEntitySchema.TypeValues.FAQ, 55L);

        WeaviateOutboxEntry entry = captureSavedEntry();
        assertThat(entry.getOperation()).isEqualTo(WeaviateOutboxOperation.DELETE_ENTITY);
        assertThat(entry.getEntityType()).isEqualTo(SearchableEntitySchema.TypeValues.FAQ);
        assertThat(entry.getEntityId()).isEqualTo(55L);
        assertThat(entry.getPayload()).isNull();
        verify(weaviateService, never()).getCollection(any());
    }

    @Test
    void testBulkDeletes_enqueueCorrectOperationAndParams() throws Exception {
        service.deleteAllForCourseAsync(1L);
        service.deleteAllPostsForCourseAsync(2L);
        service.deleteAllPostsForChannelAsync(3L);
        service.deleteAllAnswerPostsForPostAsync(4L);
        service.deleteAllLectureUnitsForLectureAsync(5L);

        ArgumentCaptor<WeaviateOutboxEntry> captor = ArgumentCaptor.forClass(WeaviateOutboxEntry.class);
        verify(outboxRepository, times(5)).save(captor.capture());
        List<WeaviateOutboxEntry> saved = captor.getAllValues();

        assertBulkDelete(saved.get(0), WeaviateOutboxOperation.DELETE_ALL_FOR_COURSE, "courseId", 1L);
        assertBulkDelete(saved.get(1), WeaviateOutboxOperation.DELETE_POSTS_FOR_COURSE, "courseId", 2L);
        assertBulkDelete(saved.get(2), WeaviateOutboxOperation.DELETE_POSTS_FOR_CHANNEL, "channelId", 3L);
        assertBulkDelete(saved.get(3), WeaviateOutboxOperation.DELETE_ANSWER_POSTS_FOR_POST, "postId", 4L);
        assertBulkDelete(saved.get(4), WeaviateOutboxOperation.DELETE_LECTURE_UNITS_FOR_LECTURE, "lectureId", 5L);
        verify(weaviateService, never()).getCollection(any());
    }

    @Test
    void testUpsertCourse_withNullDtoOrNullIdDoesNotEnqueue() {
        service.upsertCourseAsync(null);
        service.upsertCourseAsync(new CourseSearchableEntityDTO(null, "c", null, null));

        verifyNoInteractions(outboxRepository);
        verify(weaviateService, never()).getCollection(any());
    }

    private void assertBulkDelete(WeaviateOutboxEntry entry, WeaviateOutboxOperation operation, String paramKey, long paramValue) throws Exception {
        assertThat(entry.getOperation()).isEqualTo(operation);
        assertThat(entry.getEntityType()).isNull();
        assertThat(entry.getPayload()).isNull();
        Map<String, Object> params = objectMapper.readValue(entry.getParams(), new TypeReference<>() {
        });
        assertThat(((Number) params.get(paramKey)).longValue()).isEqualTo(paramValue);
    }

    private WeaviateOutboxEntry captureSavedEntry() {
        ArgumentCaptor<WeaviateOutboxEntry> captor = ArgumentCaptor.forClass(WeaviateOutboxEntry.class);
        verify(outboxRepository).save(captor.capture());
        return captor.getValue();
    }

    private static ExerciseSearchableEntityDTO exerciseDto(long id) {
        return new ExerciseSearchableEntityDTO(id, 100L, "Exercise " + id, "programming", null, null, null, null, null, null, null, false, null, null, null, null, null, null, null,
                null, null, null, null, null);
    }
}
