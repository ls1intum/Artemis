package de.tum.cit.aet.artemis.communication.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.communication.domain.PostingType;
import de.tum.cit.aet.artemis.communication.domain.SavedPost;
import de.tum.cit.aet.artemis.communication.domain.SavedPostStatus;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Profile(PROFILE_CORE)
@Repository
public interface SavedPostRepository extends ArtemisJpaRepository<SavedPost, Long> {

    Long countByUserId(Long userId);

    SavedPost findSavedPostByUserIdAndPostIdAndPostType(Long userId, Long postId, PostingType postType);

    List<SavedPost> findSavedPostsByUserIdAndStatusOrderByCompletedAtDescIdDesc(Long userId, SavedPostStatus status);

    List<SavedPost> findSavedPostsByUserId(Long userId);

    List<SavedPost> findByCompletedAtBefore(ZonedDateTime cutoffDate);

    List<SavedPost> findSavedPostsByUserIdAndPostIdInAndPostType(Long userId, List<Long> postIds, PostingType postType);

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
