package de.tum.cit.aet.artemis.communication.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.communication.domain.SavedPost;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

@Profile(PROFILE_CORE)
@Repository
public interface SavedPostRepository extends ArtemisJpaRepository<SavedPost, Long> {

    Long countByUserId(Long userId);

    SavedPost findSavedPostByUserIdAndPostIdAndPostType(Long userId, Long postId, String postType);

    List<SavedPost> findSavedPostsByUserIdAndStatusOrderById(Long userId, String status);

    List<SavedPost> findByCompletedAtBefore(ZonedDateTime cutoffDate);

    @Query("SELECT sp FROM SavedPost sp " + "LEFT JOIN Post p ON sp.postId = p.id " + "WHERE sp.postType = 'post' AND p.id IS NULL")
    List<SavedPost> findOrphanedPostReferences();

    @Query("SELECT sp FROM SavedPost sp " + "LEFT JOIN AnswerPost ap ON sp.postId = ap.id " + "WHERE sp.postType = 'answer' AND ap.id IS NULL")
    List<SavedPost> findOrphanedAnswerReferences();
}
