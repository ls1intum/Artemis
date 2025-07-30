package de.tum.cit.aet.artemis.communication.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data repository for the Message (Post) entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface ConversationMessageRepository extends ArtemisJpaRepository<Post, Long>, CustomPostRepository {

    Logger log = LoggerFactory.getLogger(ConversationMessageRepository.class);

    // Fetch only Post with direct relationships (author, conversation)—not collections.
    @EntityGraph(attributePaths = { "author", "conversation" })
    List<Post> findByIdIn(List<Long> postIds);

    @Query("SELECT p.id, t FROM Post p JOIN p.tags t WHERE p.id IN :postIds")
    List<Object[]> findTagsByPostIds(@Param("postIds") List<Long> postIds);

    @Query("""
            SELECT p
            FROM Post p
                LEFT JOIN FETCH p.author
                LEFT JOIN FETCH p.conversation
                LEFT JOIN FETCH p.reactions
                LEFT JOIN FETCH p.tags
                LEFT JOIN FETCH p.answers a
                    LEFT JOIN FETCH a.reactions
                    LEFT JOIN FETCH a.post
                    LEFT JOIN FETCH a.author
            WHERE p.id IN :postIds
            """)
    List<Post> findByPostIdsWithEagerRelationships(@Param("postIds") List<Long> postIds);

    default Post findMessagePostByIdElseThrow(Long postId) throws EntityNotFoundException {
        return getValueElseThrow(findById(postId).filter(post -> post.getConversation() != null), postId);
    }

    Integer countByConversationId(Long conversationId);

    @Query("""
            SELECT DISTINCT answer.author
            FROM Post p
                LEFT JOIN p.answers answer
                LEFT JOIN p.conversation c
                LEFT JOIN c.conversationParticipants cp
            WHERE p.id = :postId AND answer.author = cp.user
            """)
    Set<User> findUsersWhoRepliedInMessage(@Param("postId") Long postId);

    List<Long> id(Long id);
}
