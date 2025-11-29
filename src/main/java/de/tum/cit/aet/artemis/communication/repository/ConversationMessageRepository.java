package de.tum.cit.aet.artemis.communication.repository;

import static de.tum.cit.aet.artemis.communication.repository.MessageSpecs.getAnsweredOrReactedSpecification;
import static de.tum.cit.aet.artemis.communication.repository.MessageSpecs.getConversationsSpecification;
import static de.tum.cit.aet.artemis.communication.repository.MessageSpecs.getCourseWideChannelsSpecification;
import static de.tum.cit.aet.artemis.communication.repository.MessageSpecs.getPinnedSpecification;
import static de.tum.cit.aet.artemis.communication.repository.MessageSpecs.getSearchTextAndAuthorSpecification;
import static de.tum.cit.aet.artemis.communication.repository.MessageSpecs.getSortSpecification;
import static de.tum.cit.aet.artemis.communication.repository.MessageSpecs.getUnresolvedSpecification;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.dto.PostContextFilterDTO;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.core.util.TimeLogUtil;

/**
 * Spring Data repository for the Message (Post) entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface ConversationMessageRepository extends ArtemisJpaRepository<Post, Long>, CustomPostRepository {

    Logger log = LoggerFactory.getLogger(ConversationMessageRepository.class);

    /**
     * Configures the search specifications based on the provided filter criteria.
     *
     * @param specification     The existing specification to be configured.
     * @param postContextFilter Filtering and sorting properties for post objects.
     * @param userId            The id of the user for which the messages should be returned.
     * @return A Specification object configured with search criteria.
     */
    private Specification<Post> configureSearchSpecification(Specification<Post> specification, PostContextFilterDTO postContextFilter, long userId) {
        return specification
        // @formatter:off
            .and(getSearchTextAndAuthorSpecification(postContextFilter.searchText(), postContextFilter.authorIds()))
            .and(getCourseWideChannelsSpecification(Boolean.TRUE.equals(postContextFilter.filterToCourseWide()), postContextFilter.courseId()))
            .and(getAnsweredOrReactedSpecification(Boolean.TRUE.equals(postContextFilter.filterToAnsweredOrReacted()), userId))
            .and(getUnresolvedSpecification(Boolean.TRUE.equals(postContextFilter.filterToUnresolved())))
            .and(getPinnedSpecification(Boolean.TRUE.equals(postContextFilter.pinnedOnly())))
            .and(getSortSpecification(true, postContextFilter.postSortCriterion(), postContextFilter.sortingOrder()));
            // @formatter:on
    }

    /**
     * Generates SQL Query via specifications to find and sort Messages
     *
     * @param postContextFilter filtering and sorting properties for post objects
     * @param pageable          paging object which contains the page number and number of records to fetch
     * @param userId            the id of the user for which the messages should be returned
     * @return returns a Page of Messages
     */
    default Page<Post> findMessages(PostContextFilterDTO postContextFilter, Pageable pageable, long userId) {
        var specification = getConversationsSpecification(postContextFilter.conversationIds());
        specification = configureSearchSpecification(specification, postContextFilter, userId);
        // Fetch all necessary attributes to avoid lazy loading (even though relations are defined as EAGER in the domain class, specification queries do not respect this)
        return findPostsWithSpecification(pageable, specification);
    }

    private PageImpl<Post> findPostsWithSpecification(Pageable pageable, Specification<Post> specification) {
        // Only fetch the postIds without any left joins to avoid that Hibernate loads all objects and creates the page in Java
        long start = System.nanoTime();
        Page<Long> postIds = findPostIdsWithSpecification(specification, pageable);
        log.debug("findPostIdsWithSpecification took {}", TimeLogUtil.formatDurationFrom(start));
        // Fetch all necessary attributes to avoid lazy loading (even though relations are defined as EAGER in the domain class, specification queries do not respect this)
        long start2 = System.nanoTime();
        List<Post> posts = findByPostIdsWithEagerRelationships(postIds.getContent());
        // Make sure to sort the posts in the same order as the postIds
        Map<Long, Post> postMap = posts.stream().collect(Collectors.toMap(Post::getId, post -> post));
        posts = postIds.stream().map(postMap::get).toList();
        log.info("findByPostIdsWithEagerRelationships took {}", TimeLogUtil.formatDurationFrom(start2));
        // Recreate the page with the fetched posts
        return new PageImpl<>(posts, postIds.getPageable(), postIds.getTotalElements());
    }

    @Query("""
            SELECT p
            FROM Post p
                LEFT JOIN FETCH p.author
                LEFT JOIN FETCH p.conversation
                LEFT JOIN FETCH p.reactions r1
                    LEFT JOIN FETCH r1.user
                LEFT JOIN FETCH p.answers a
                    LEFT JOIN FETCH a.reactions r2
                        LEFT JOIN FETCH r2.user
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
}
