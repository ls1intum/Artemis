package de.tum.cit.aet.artemis.communication.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.communication.domain.PostingType;
import de.tum.cit.aet.artemis.communication.domain.SavedPost;
import de.tum.cit.aet.artemis.communication.domain.SavedPostStatus;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Profile(PROFILE_CORE)
@Repository
@CacheConfig(cacheNames = "savedPosts")
public interface SavedPostRepository extends ArtemisJpaRepository<SavedPost, Long> {

    /***
     * Get the amount of saved posts of a user. E.g. for checking if maximum allowed bookmarks are reached.
     * Cached by user id.
     *
     * @param userId to query for
     *
     * @return The amount of bookmarks of the user.
     */
    @Cacheable(key = "'saved_post_count_' + #userId")
    Long countByUserId(Long userId);

    /***
     * Get all saved post by user id, connected post/answer post id and posting type. Not cached.
     *
     * @param userId   of the bookmark
     * @param postId   of the bookmark
     * @param postType of the bookmark
     *
     * @return The saved posts if they exist.
     */
    List<SavedPost> findSavedPostsByUserIdAndPostIdAndPostType(Long userId, Long postId, PostingType postType);

    /***
     * Get all saved posts by connected post/answer post id and posting type. Not cached.
     *
     * @param postId   of the bookmark
     * @param postType of the bookmark
     *
     * @return List of all saved posts connected to the corresponding entity.
     */
    List<SavedPost> findSavedPostByPostIdAndPostType(Long postId, PostingType postType);

    /***
     * Query all post ids that a user has saved by a certain posting type. Cached by user id and post type.
     *
     * @param userId   of the bookmarks
     * @param postType of the bookmarks
     *
     * @return List of ids of posts/answer posts of the given user, filtered by the given post type.
     */
    @Query("""
                SELECT s.postId
                FROM SavedPost s
                WHERE s.user.id = :userId AND s.postType = :postType
            """)
    @Cacheable(key = "'saved_post_type_' + #postType.getDatabaseKey() + '_' + #userId")
    List<Long> findSavedPostIdsByUserIdAndPostType(@Param("userId") long userId, @Param("postType") PostingType postType);

    /***
     * Query all saved posts of a user by status. E.g. for displaying the saved posts. Cached by user id and status.
     *
     * @param userId of the bookmarks
     * @param status of the bookmarks
     *
     * @return List of saved posts of the given user, filtered by the given status.
     */
    @Query("""
            SELECT new SavedPost(sp.user, sp.postId, sp.postType, MAX(sp.status), MAX(sp.completedAt))
            FROM SavedPost sp
            WHERE sp.user.id = :userId
                AND sp.status = :status
            GROUP BY sp.user, sp.postId, sp.postType
            ORDER BY MAX(sp.completedAt) DESC, MAX(sp.id) DESC
            """)
    @Cacheable(key = "'saved_post_status_' + #status.getDatabaseKey() + '_' + #userId")
    List<SavedPost> findSavedPostsByUserIdAndStatusOrderByCompletedAtDescIdDesc(@Param("userId") long userId, @Param("status") SavedPostStatus status);

    /***
     * Query all SavedPosts for a certain user. Not cached.
     *
     * @param userId of the bookmarks
     *
     * @return List of saved posts of the given user.
     */
    List<SavedPost> findSavedPostsByUserId(Long userId);

    /***
     * Query to get all SavedPosts that are completed before a certain cutoff date. E.g. for cleanup.
     *
     * @param cutoffDate the date from where to query the saved posts
     *
     * @return List of saved posts which were completed before the given date
     */
    List<SavedPost> findByCompletedAtBefore(ZonedDateTime cutoffDate);

    /***
     * Saving should clear the cached queries for a given user
     * The value "saved_post_type_0" represents a post, given by the enum {{@link PostingType}}
     * The value "saved_post_type_1" represents an answer post, given by the enum {{@link PostingType}}
     * The value "saved_post_status_0" represents in progress, given by the enum {{@link SavedPostStatus}}
     * The value "saved_post_status_1" represents in completed, given by the enum {{@link SavedPostStatus}}
     * The value "saved_post_status_2" represents in archived, given by the enum {{@link SavedPostStatus}}
     *
     * @param savedPost to create / update
     *
     * @return Newly stored saved post
     */
    @Caching(evict = { @CacheEvict(key = "'saved_post_type_0_' + #savedPost.user.id"), @CacheEvict(key = "'saved_post_type_1_' + #savedPost.user.id"),
            @CacheEvict(key = "'saved_post_status_0_' + #savedPost.user.id"), @CacheEvict(key = "'saved_post_status_1_' + #savedPost.user.id"),
            @CacheEvict(key = "'saved_post_status_2_' + #savedPost.user.id"), @CacheEvict(key = "'saved_post_count_' + #savedPost.user.id"), })
    @Override
    <S extends SavedPost> S save(S savedPost);

    /***
     * Deleting should clear the cached queries for a given user
     * The value "saved_post_type_0" represents a post, given by the enum {{@link PostingType}}
     * The value "saved_post_type_1" represents an answer post, given by the enum {{@link PostingType}}
     * The value "saved_post_status_0" represents in progress, given by the enum {{@link SavedPostStatus}}
     * The value "saved_post_status_1" represents in completed, given by the enum {{@link SavedPostStatus}}
     * The value "saved_post_status_2" represents in archived, given by the enum {{@link SavedPostStatus}}
     *
     * @param savedPost to delete
     */
    @Caching(evict = { @CacheEvict(key = "'saved_post_type_0_' + #savedPost.user.id"), @CacheEvict(key = "'saved_post_type_1_' + #savedPost.user.id"),
            @CacheEvict(key = "'saved_post_status_0_' + #savedPost.user.id"), @CacheEvict(key = "'saved_post_status_1_' + #savedPost.user.id"),
            @CacheEvict(key = "'saved_post_status_2_' + #savedPost.user.id"), @CacheEvict(key = "'saved_post_count_' + #savedPost.user.id"), })
    @Override
    void delete(SavedPost savedPost);

    /***
     * The value "sp.postType = 0" represents a post, given by the enum {{@link PostingType}}
     *
     * @return List of saved posts that do not have a post entity connected to them
     */
    @Query("SELECT sp FROM SavedPost sp " + "LEFT JOIN Post p ON sp.postId = p.id " + "WHERE sp.postType = 0 AND p.id IS NULL")
    List<SavedPost> findOrphanedPostReferences();

    /***
     * The value "sp.postType = 1" represents an answer post, given by the enum {{@link PostingType}}
     *
     * @return List of saved posts that do not have an answer post entity connected to them
     */
    @Query("SELECT sp FROM SavedPost sp " + "LEFT JOIN AnswerPost ap ON sp.postId = ap.id " + "WHERE sp.postType = 1 AND ap.id IS NULL")
    List<SavedPost> findOrphanedAnswerReferences();
}
