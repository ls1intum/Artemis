package de.tum.cit.aet.artemis.globalsearch.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.repository.FaqRepository;
import de.tum.cit.aet.artemis.communication.repository.PostRepository;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.exam.api.ExamRepositoryApi;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.AnswerPostSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.ChannelSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.CourseSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.ExamSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.ExerciseSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.FaqSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.LectureSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.LectureUnitSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.PostSearchableEntityDTO;
import de.tum.cit.aet.artemis.lecture.api.LectureRepositoryApi;
import de.tum.cit.aet.artemis.lecture.api.LectureUnitRepositoryApi;

/**
 * Re-derives the current desired Weaviate state of a single entity from the database (the source of truth) at
 * dispatch time.
 * <p>
 * The outbox stores only the identity {@code (type, entityId)} of a change, never a content snapshot. When the
 * dispatcher applies an upsert it calls {@link #resolve(String, long)}, which loads the entity from Postgres and
 * rebuilds its property map exactly as the enqueue-time call site would:
 * <ul>
 * <li>a present, indexable entity resolves to its current property map (the dispatcher upserts it);</li>
 * <li>an entity that has since been deleted, or has become non-indexable (for example a post whose channel was
 * archived or made private), resolves to {@link Optional#empty()} (the dispatcher deletes its row instead).</li>
 * </ul>
 * Because every apply reflects current truth rather than a captured intent, a backed-off or reordered upsert can
 * never resurrect a deleted or hidden entity, and content never goes stale. All the associations the DTOs read
 * (course, lecture, channel, exam) are eager {@code @ManyToOne}s, so a plain {@code findById} materializes them
 * without a fetch join or an open-session-in-view.
 */
@Lazy
@Service
@Conditional(WeaviateEnabled.class)
public class SearchableEntityResolver {

    private final CourseRepository courseRepository;

    private final FaqRepository faqRepository;

    private final PostRepository postRepository;

    private final AnswerPostRepository answerPostRepository;

    private final ChannelRepository channelRepository;

    private final ExerciseSearchableEntityLoadService exerciseLoadService;

    private final Optional<ExamRepositoryApi> examRepositoryApi;

    private final Optional<LectureRepositoryApi> lectureRepositoryApi;

    private final Optional<LectureUnitRepositoryApi> lectureUnitRepositoryApi;

    public SearchableEntityResolver(CourseRepository courseRepository, FaqRepository faqRepository, PostRepository postRepository, AnswerPostRepository answerPostRepository,
            ChannelRepository channelRepository, ExerciseSearchableEntityLoadService exerciseLoadService, Optional<ExamRepositoryApi> examRepositoryApi,
            Optional<LectureRepositoryApi> lectureRepositoryApi, Optional<LectureUnitRepositoryApi> lectureUnitRepositoryApi) {
        this.courseRepository = courseRepository;
        this.faqRepository = faqRepository;
        this.postRepository = postRepository;
        this.answerPostRepository = answerPostRepository;
        this.channelRepository = channelRepository;
        this.exerciseLoadService = exerciseLoadService;
        this.examRepositoryApi = examRepositoryApi;
        this.lectureRepositoryApi = lectureRepositoryApi;
        this.lectureUnitRepositoryApi = lectureUnitRepositoryApi;
    }

    /**
     * Resolves the current property map an entity should have in the {@code SearchableEntities} collection.
     *
     * @param type     the {@code SearchableEntitySchema.TypeValues} discriminator
     * @param entityId the database id of the entity
     * @return the current property map if the entity exists and is indexable, otherwise {@link Optional#empty()}
     */
    public Optional<Map<String, Object>> resolve(String type, long entityId) {
        return switch (type) {
            case SearchableEntitySchema.TypeValues.COURSE -> courseRepository.findById(entityId).map(course -> CourseSearchableEntityDTO.fromCourse(course).toPropertyMap());
            case SearchableEntitySchema.TypeValues.EXERCISE -> exerciseLoadService.loadExerciseDtoForResolve(entityId).map(ExerciseSearchableEntityDTO::toPropertyMap);
            case SearchableEntitySchema.TypeValues.LECTURE ->
                lectureRepositoryApi.flatMap(api -> api.findById(entityId)).map(lecture -> LectureSearchableEntityDTO.fromLecture(lecture).toPropertyMap());
            case SearchableEntitySchema.TypeValues.LECTURE_UNIT -> lectureUnitRepositoryApi.map(api -> api.findAllByIdsWithLecture(List.of(entityId))).orElseGet(List::of).stream()
                    .findFirst().filter(LectureUnitSearchableEntityDTO::isIndexable).map(unit -> LectureUnitSearchableEntityDTO.fromLectureUnit(unit).toPropertyMap());
            case SearchableEntitySchema.TypeValues.EXAM ->
                examRepositoryApi.flatMap(api -> api.findById(entityId)).map(exam -> ExamSearchableEntityDTO.fromExam(exam).toPropertyMap());
            case SearchableEntitySchema.TypeValues.FAQ -> faqRepository.findById(entityId).map(faq -> FaqSearchableEntityDTO.fromFaq(faq).toPropertyMap());
            case SearchableEntitySchema.TypeValues.CHANNEL -> channelRepository.findById(entityId).filter(ChannelSearchableEntityDTO::isIndexable)
                    .map(channel -> ChannelSearchableEntityDTO.fromChannel(channel).toPropertyMap());
            case SearchableEntitySchema.TypeValues.POST -> postRepository.findById(entityId).flatMap(SearchableEntityResolver::resolvePost);
            case SearchableEntitySchema.TypeValues.ANSWER_POST -> answerPostRepository.findById(entityId).flatMap(SearchableEntityResolver::resolveAnswerPost);
            default -> throw new IllegalStateException("Unknown searchable entity type for re-derivation: " + type);
        };
    }

    /**
     * A post is indexable only when it lives in an indexable channel; other conversation kinds are never indexed.
     */
    private static Optional<Map<String, Object>> resolvePost(Post post) {
        if (post.getConversation() instanceof Channel channel && PostSearchableEntityDTO.isIndexable(channel)) {
            return Optional.of(PostSearchableEntityDTO.fromPost(post, channel).toPropertyMap());
        }
        return Optional.empty();
    }

    /**
     * An answer post follows the indexability of its parent post's channel.
     */
    private static Optional<Map<String, Object>> resolveAnswerPost(AnswerPost answerPost) {
        if (answerPost.getPost() != null && answerPost.getPost().getConversation() instanceof Channel channel && PostSearchableEntityDTO.isIndexable(channel)) {
            return Optional.of(AnswerPostSearchableEntityDTO.fromAnswerPost(answerPost, channel).toPropertyMap());
        }
        return Optional.empty();
    }
}
