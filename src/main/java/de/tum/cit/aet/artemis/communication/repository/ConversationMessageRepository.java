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

import java.util.Collection;
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
     * Increments a thread's Course Memory version atomically in the database, as the first half of minting the
     * version of an ingestion or deletion about to be dispatched (see {@code CourseMemoryIngestionService}).
     * <p>
     * A native statement rather than an entity save on purpose: the increment has to be atomic across Artemis
     * nodes, and the row lock it takes serialises concurrent minting so no two operations on a thread ever
     * share a version. The entity maps the column as neither insertable nor updatable, so this is the only
     * writer. {@link #mintCourseMemoryVersion(long)} reads the minted value back inside the same transaction,
     * while the lock is still held; call that rather than this directly.
     *
     * @param postId the id of the thread's root post
     */
    @Transactional // ok because of modifying query
    @Modifying
    @Query(value = "UPDATE post SET course_memory_version = course_memory_version + 1 WHERE id = :postId", nativeQuery = true)
    void incrementCourseMemoryVersion(@Param("postId") long postId);

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
     * Mints the next Course Memory version of a thread: increments the counter and reads the result back in one
     * transaction, so the row lock taken by the increment still serialises concurrent minting when the value is
     * read. Two operations on one thread can therefore never share a version, on however many nodes they run.
     * The boundary lives here rather than in the calling service, which is where Artemis defines them.
     *
     * @param postId the id of the thread's root post
     * @return the minted version, or empty if the post no longer exists
     */
    @Transactional // ok because the increment and the read-back of the minted value have to share the row lock
    default Optional<Long> mintCourseMemoryVersion(long postId) {
        incrementCourseMemoryVersion(postId);
        return findCourseMemoryVersion(postId);
    }

    /**
     * Reads a thread's Course Memory version and locks its row until the transaction ends.
     *
     * @param postId the id of the thread's root post
     * @return the version, or empty if the post does not exist
     */
    @Query(value = "SELECT course_memory_version FROM post WHERE id = :postId FOR UPDATE", nativeQuery = true)
    Optional<Long> lockCourseMemoryVersion(@Param("postId") long postId);

    /**
     * Deletes a thread and returns the Course Memory version it had. The row stays locked from the read to the deletion,
     * so a version minted concurrently is either seen here or never minted at all (the mint waits for the lock and then
     * finds no row). A caller that sees 0 can therefore skip the retraction: no entry can exist or appear.
     *
     * @param postId the id of the thread's root post
     * @return the thread's Course Memory version before the deletion, 0 if it never had one
     */
    @Transactional // ok because the version has to be read under the same row lock as the deletion
    default long deleteAndReturnCourseMemoryVersion(long postId) {
        long version = lockCourseMemoryVersion(postId).orElse(0L);
        deleteById(postId);
        return version;
    }

    /**
     * Bumps a thread's Course Memory version if the thread has one, i.e. if anything was ever dispatched for it. Used in the
     * same transaction as a change to the thread's content, so the entry Pyris holds is outdated the moment the change
     * commits; if the refresh that follows never reaches Pyris, the nightly sync retracts the entry.
     *
     * @param postId the id of the thread's root post
     * @return 1 if the version was bumped, 0 if the thread has no Course Memory version
     */
    @Transactional // ok because of modifying query
    @Modifying
    @Query(value = "UPDATE post SET course_memory_version = course_memory_version + 1 WHERE id = :postId AND course_memory_version > 0", nativeQuery = true)
    int bumpCourseMemoryVersionIfTracked(@Param("postId") long postId);

    /**
     * Bumps the Course Memory version of every given thread that has one.
     *
     * @param postIds the ids of the threads' root posts
     * @return how many versions were bumped
     */
    @Transactional // ok because of modifying query
    @Modifying
    @Query(value = "UPDATE post SET course_memory_version = course_memory_version + 1 WHERE id IN (:postIds) AND course_memory_version > 0", nativeQuery = true)
    int bumpCourseMemoryVersions(@Param("postIds") Collection<Long> postIds);

    /**
     * Saves an edited root post and outdates the thread's Course Memory entry in one transaction.
     *
     * @param post the edited post
     * @return the saved post
     */
    @Transactional // ok because the edit and the version bump have to commit together
    default Post saveAndInvalidateCourseMemory(Post post) {
        Post saved = save(post);
        bumpCourseMemoryVersionIfTracked(saved.getId());
        return saved;
    }

    /**
     * The threads with a Course Memory version that contain a message by the given user, as root post or as answer.
     *
     * @param userId the user
     * @return the threads' root post ids
     */
    @Query("""
            SELECT DISTINCT post.id
            FROM Post post
            WHERE post.courseMemoryVersion > 0
                AND (post.author.id = :userId
                    OR EXISTS (SELECT answer.id FROM AnswerPost answer WHERE answer.post.id = post.id AND answer.author.id = :userId))
            """)
    List<Long> findCourseMemoryThreadIdsWithContentBy(@Param("userId") long userId);

    /**
     * Outdates the Course Memory entries of every thread that contains a message by the given user, in one transaction.
     * Called before an opt-out from AI or a deactivation is recorded.
     *
     * @param userId the user
     * @return the affected threads' root post ids
     */
    @Transactional // ok because the lookup and the version bump have to see the same threads
    default List<Long> invalidateCourseMemoryOfThreadsWithContentBy(long userId) {
        List<Long> postIds = findCourseMemoryThreadIdsWithContentBy(userId);
        if (!postIds.isEmpty()) {
            bumpCourseMemoryVersions(postIds);
        }
        return postIds;
    }

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
     * @param postId the id of a post
     * @return the id of the course the post's conversation belongs to, or empty if the post does not exist
     */
    @Query("""
            SELECT post.conversation.course.id
            FROM Post post
            WHERE post.id = :postId
            """)
    Optional<Long> findCourseIdOfPost(@Param("postId") long postId);

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
