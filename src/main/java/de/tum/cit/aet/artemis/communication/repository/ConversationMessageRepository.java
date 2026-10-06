package de.tum.cit.aet.artemis.communication.repository;

import static de.tum.cit.aet.artemis.communication.repository.MessageSpecs.getAnsweredOrReactedSpecification;
import static de.tum.cit.aet.artemis.communication.repository.MessageSpecs.getConversationsSpecification;
import static de.tum.cit.aet.artemis.communication.repository.MessageSpecs.getCourseWideChannelsSpecification;
import static de.tum.cit.aet.artemis.communication.repository.MessageSpecs.getPinnedSpecification;
import static de.tum.cit.aet.artemis.communication.repository.MessageSpecs.getSearchTextAndAuthorSpecification;
import static de.tum.cit.aet.artemis.communication.repository.MessageSpecs.getSortSpecification;
import static de.tum.cit.aet.artemis.communication.repository.MessageSpecs.getUnresolvedSpecification;
import static de.tum.cit.aet.artemis.communication.repository.MessageSpecs.getUnverifiedIrisAnswersSpecification;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.dto.CourseMemoryThreadDTO;
import de.tum.cit.aet.artemis.communication.dto.PostContextFilterDTO;
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

    /**
     * How often {@link #mintCourseMemoryVersion(long)} retries a compare-and-set that lost against a concurrent mint or bump.
     */
    int MAX_COURSE_MEMORY_MINT_ATTEMPTS = 20;

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
            .and(getUnverifiedIrisAnswersSpecification(Boolean.TRUE.equals(postContextFilter.filterToUnverifiedIris()), User.IRIS_BOT_LOGIN))
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
        log.debug("findByPostIdsWithEagerRelationships took {}", TimeLogUtil.formatDurationFrom(start2));
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

    /**
     * Sets a thread's Course Memory version to {@code next}, but only if it is still {@code current}. The check in the
     * {@code WHERE} clause makes the update a compare-and-set: of several callers that read the same version, exactly one
     * succeeds. {@link #mintCourseMemoryVersion(long)} retries the others.
     *
     * @param postId  the id of the thread's root post
     * @param current the version the caller read
     * @param next    the version to set
     * @return 1 if the version was set, 0 if it had changed in the meantime or the post does not exist
     */
    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE Post post
            SET post.courseMemoryVersion = :next
            WHERE post.id = :postId
                AND post.courseMemoryVersion = :current
            """)
    int compareAndSetCourseMemoryVersion(@Param("postId") long postId, @Param("current") long current, @Param("next") long next);

    /**
     * Reads a thread's current Course Memory version straight from the database, bypassing any loaded entity
     * whose copy may be stale.
     *
     * @param postId the id of the thread's root post
     * @return the version, or empty if the post no longer exists
     */
    @Query("""
            SELECT p.courseMemoryVersion
            FROM Post p
            WHERE p.id = :postId
            """)
    Optional<Long> findCourseMemoryVersion(@Param("postId") long postId);

    /**
     * Mints the next Course Memory version of a thread. Two operations on one thread never share a version, on however
     * many nodes they run: the version is read and then set with a compare-and-set, which fails if anyone minted or bumped
     * in between, and is then retried. No transaction is needed, because each attempt is one read and one single-statement
     * update.
     *
     * @param postId the id of the thread's root post
     * @return the minted version, or empty if the post no longer exists
     */
    default Optional<Long> mintCourseMemoryVersion(long postId) {
        for (int attempt = 0; attempt < MAX_COURSE_MEMORY_MINT_ATTEMPTS; attempt++) {
            Optional<Long> current = findCourseMemoryVersion(postId);
            if (current.isEmpty()) {
                return Optional.empty();
            }
            long next = current.get() + 1;
            if (compareAndSetCourseMemoryVersion(postId, current.get(), next) == 1) {
                return Optional.of(next);
            }
        }
        throw new IllegalStateException("Could not mint a Course Memory version for post " + postId);
    }

    /**
     * Bumps a thread's Course Memory version if the thread has one, i.e. if anything was ever dispatched for it.
     * <p>
     * Every change that can make a stored entry outdated bumps the version twice, right before and right after it is
     * saved, and then refreshes the thread. Without a transaction around the change (not allowed outside a single
     * modifying query) both bumps are needed:
     * <ul>
     * <li>The bump before the save outdates the stored entry even if the save then fails or the process stops.</li>
     * <li>The bump after the save covers a refresh that ran in between: it minted its version after the first bump but
     * read the state from before the save. The second bump makes that version older than Artemis', so its entry is
     * replaced by the follow-up refresh or, if that never runs, retracted by the nightly sync.</li>
     * </ul>
     *
     * @param postId the id of the thread's root post
     * @return 1 if the version was bumped, 0 if the thread has no Course Memory version
     */
    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE Post post
            SET post.courseMemoryVersion = post.courseMemoryVersion + 1
            WHERE post.id = :postId
                AND post.courseMemoryVersion > 0
            """)
    int bumpCourseMemoryVersionIfTracked(@Param("postId") long postId);

    /**
     * @param postId the id of a post
     * @return the id of the conversation the post belongs to, or empty if the post does not exist
     */
    @Query("""
            SELECT post.conversation.id
            FROM Post post
            WHERE post.id = :postId
            """)
    Optional<Long> findConversationIdOfPost(@Param("postId") long postId);

    /**
     * @return the ids of all courses with at least one thread that has a Course Memory version
     */
    @Query("""
            SELECT DISTINCT post.conversation.course.id
            FROM Post post
            WHERE post.courseMemoryVersion > 0
            """)
    Set<Long> findCourseIdsWithCourseMemory();

    /**
     * The next threads of a course that have a Course Memory version, in id order after {@code afterPostId}. Keyset paging,
     * so a thread that gets its first version while the list is read cannot shift a page and hide another thread.
     *
     * @param courseId    the course
     * @param afterPostId the last post id of the previous page, or 0 for the first page
     * @param pageable    the page size (the page number must be 0)
     * @return the threads with their channel and current version
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.communication.dto.CourseMemoryThreadDTO(post.id, post.conversation.id, post.conversation.course.id, post.courseMemoryVersion)
            FROM Post post
            WHERE post.conversation.course.id = :courseId
                AND post.courseMemoryVersion > 0
                AND post.id > :afterPostId
            ORDER BY post.id
            """)
    List<CourseMemoryThreadDTO> findCourseMemoryThreadsOfCourseAfter(@Param("courseId") long courseId, @Param("afterPostId") long afterPostId, Pageable pageable);

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
