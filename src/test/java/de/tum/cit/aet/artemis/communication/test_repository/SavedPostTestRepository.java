package de.tum.cit.aet.artemis.communication.test_repository;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.communication.domain.PostingType;
import de.tum.cit.aet.artemis.communication.domain.SavedPost;
import de.tum.cit.aet.artemis.communication.repository.SavedPostRepository;

@Repository
@Primary
public interface SavedPostTestRepository extends SavedPostRepository {

    /***
     * Get a single saved post by user id, connected post/answer post id and posting type. Not cached.
     * Be careful: There may be multiple entries for a post id/type id/user id. Make sure to use this
     * when know there is only a single one (e.g. certain test cases). Will throw otherwise.
     *
     * @param userId   of the bookmark
     * @param postId   of the bookmark
     * @param postType of the bookmark
     *
     * @return The saved post if exists, null otherwise.
     */
    SavedPost findSavedPostByUserIdAndPostIdAndPostType(Long userId, Long postId, PostingType postType);
}
