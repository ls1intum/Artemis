package de.tum.cit.aet.artemis.globalsearch.service.reconcile;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.exam.api.ExamRepositoryApi;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.repository.SearchableEntityIdRepository;
import de.tum.cit.aet.artemis.lecture.api.LectureRepositoryApi;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitRepositoryApi;

/**
 * Walks the ids the database expects to be indexed, one bounded page per call, for any entity type.
 * <p>
 * The counterpart to {@code SearchableEntityResolver}: that one answers what a single entity should look like,
 * this one answers which entities exist at all. A pass needs both, and neither should have to know where a given
 * type's rows live.
 * <p>
 * Types owned by optional modules return {@link Optional#empty()} when that module is disabled. That case must
 * stay distinguishable from an empty page, because they mean opposite things: an empty page means the type is
 * fully walked, while a disabled module means nothing at all is known about it. Treating the second as the first
 * would let a pass conclude that every lecture is missing from the index, or that every indexed lecture is an
 * orphan.
 */
@Lazy
@Service
@Conditional(WeaviateEnabled.class)
public class SearchableEntityIdEnumerator {

    private final SearchableEntityIdRepository idRepository;

    private final Optional<LectureRepositoryApi> lectureRepositoryApi;

    private final Optional<LectureUnitRepositoryApi> lectureUnitRepositoryApi;

    private final Optional<ExamRepositoryApi> examRepositoryApi;

    public SearchableEntityIdEnumerator(SearchableEntityIdRepository idRepository, Optional<LectureRepositoryApi> lectureRepositoryApi,
            Optional<LectureUnitRepositoryApi> lectureUnitRepositoryApi, Optional<ExamRepositoryApi> examRepositoryApi) {
        this.idRepository = idRepository;
        this.lectureRepositoryApi = lectureRepositoryApi;
        this.lectureUnitRepositoryApi = lectureUnitRepositoryApi;
        this.examRepositoryApi = examRepositoryApi;
    }

    /**
     * Reads the next page of ids that should be indexed for a type.
     *
     * @param entityType the {@code SearchableEntitySchema.TypeValues} discriminator
     * @param afterId    the id the previous page stopped at; pass 0 to start from the beginning
     * @param limit      the page size
     * @return the next ids in ascending order, empty list once the type is fully walked, or
     *         {@link Optional#empty()} if the module owning this type is disabled
     */
    public Optional<List<Long>> nextIndexableIds(String entityType, long afterId, int limit) {
        return switch (entityType) {
            case SearchableEntitySchema.TypeValues.COURSE -> Optional.of(idRepository.findCourseIdsAfter(afterId, PageRequest.ofSize(limit)));
            case SearchableEntitySchema.TypeValues.EXERCISE -> Optional.of(idRepository.findExerciseIdsAfter(afterId, PageRequest.ofSize(limit)));
            case SearchableEntitySchema.TypeValues.FAQ -> Optional.of(idRepository.findFaqIdsAfter(afterId, PageRequest.ofSize(limit)));
            case SearchableEntitySchema.TypeValues.CHANNEL -> Optional.of(idRepository.findIndexableChannelIdsAfter(afterId, PageRequest.ofSize(limit)));
            case SearchableEntitySchema.TypeValues.POST -> Optional.of(idRepository.findIndexablePostIdsAfter(afterId, PageRequest.ofSize(limit)));
            case SearchableEntitySchema.TypeValues.ANSWER_POST -> Optional.of(idRepository.findIndexableAnswerPostIdsAfter(afterId, PageRequest.ofSize(limit)));
            case SearchableEntitySchema.TypeValues.LECTURE -> lectureRepositoryApi.map(api -> api.findLectureIdsAfter(afterId, limit));
            case SearchableEntitySchema.TypeValues.LECTURE_UNIT -> lectureUnitRepositoryApi.map(api -> api.findIndexableUnitIdsAfter(afterId, limit));
            case SearchableEntitySchema.TypeValues.EXAM -> examRepositoryApi.map(api -> api.findExamIdsAfter(afterId, limit));
            default -> throw new IllegalStateException("Unknown searchable entity type for reconcile enumeration: " + entityType);
        };
    }
}
