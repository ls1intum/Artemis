package de.tum.cit.aet.artemis.globalsearch.repository;

import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateEnabled;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.AnswerPostSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.PostSearchableEntityDTO;

/**
 * Projects a single post or answer post straight into its searchable DTO, for {@code SearchableEntityResolver}'s
 * dispatch-time re-derivation.
 * <p>
 * {@code Post.reactions}, {@code Post.answers}, and {@code AnswerPost.reactions} are mapped
 * {@code FetchType.EAGER} (see {@code FIELDS_ALLOWED_TO_FETCH_EAGERLY}), so a plain {@code findById} on either
 * entity pulls in that whole graph — for a post, every answer post it has and each of those answer posts' own
 * reactions — even though the indexable property map needs none of it. Selecting straight into the DTO below
 * never instantiates the entity, so that graph is never fetched.
 * <p>
 * These queries live here rather than on {@code PostRepository}/{@code AnswerPostRepository} because they exist
 * only for search re-derivation, following the same consumer-owns-its-queries pattern as
 * {@link SearchableEntityIdRepository}. The indexability condition mirrors
 * {@link SearchableEntityIdRepository#findIndexablePostIds} exactly: it must stay in step with the write path, or
 * a resolve will disagree with the queue that fed it.
 */
@Conditional(WeaviateEnabled.class)
@Lazy
@Repository
public interface SearchableEntityPostRepository extends ArtemisJpaRepository<Post, Long> {

    /**
     * @param postId the post id
     * @return the post's searchable DTO if it exists and belongs to an indexable channel, otherwise empty
     */
    @Query("""
            SELECT NEW de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.PostSearchableEntityDTO(
                post.id, channel.course.id, channel.id, post.title, post.content)
            FROM Post post
                JOIN TREAT (post.conversation AS Channel) channel
            WHERE post.id = :postId
                AND channel.isArchived = FALSE
                AND channel.isPublic = TRUE
            """)
    Optional<PostSearchableEntityDTO> findIndexablePostProjection(@Param("postId") long postId);

    /**
     * @param answerPostId the answer post id
     * @return the answer post's searchable DTO if it exists and its parent post belongs to an indexable channel,
     *         otherwise empty
     */
    @Query("""
            SELECT NEW de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.AnswerPostSearchableEntityDTO(
                answerPost.id, post.id, channel.course.id, channel.id, answerPost.content)
            FROM AnswerPost answerPost
                JOIN answerPost.post post
                JOIN TREAT (post.conversation AS Channel) channel
            WHERE answerPost.id = :answerPostId
                AND channel.isArchived = FALSE
                AND channel.isPublic = TRUE
            """)
    Optional<AnswerPostSearchableEntityDTO> findIndexableAnswerPostProjection(@Param("answerPostId") long answerPostId);
}
