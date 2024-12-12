package de.tum.cit.aet.artemis.communication.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.Posting;
import de.tum.cit.aet.artemis.communication.domain.PostingType;
import de.tum.cit.aet.artemis.communication.domain.SavedPost;
import de.tum.cit.aet.artemis.communication.domain.SavedPostStatus;
import de.tum.cit.aet.artemis.communication.repository.SavedPostRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;

@Profile(PROFILE_CORE)
@Service
public class SavedPostService {

    private static final int MAX_SAVED_POSTS_PER_USER = 100;

    private final SavedPostRepository savedPostRepository;

    private final UserRepository userRepository;

    public SavedPostService(SavedPostRepository savedPostRepository, UserRepository userRepository) {
        this.savedPostRepository = savedPostRepository;
        this.userRepository = userRepository;
    }

    /**
     * Saves a post for the currently logged-in user, if post is already saved it returns
     *
     * @param post post to save
     */
    public void savePostForCurrentUser(Posting post) {
        var existingSavedPost = this.getSavedPostForCurrentUser(post);

        if (existingSavedPost != null) {
            return;
        }

        PostingType type = post instanceof Post ? PostingType.POST : PostingType.ANSWER;
        var author = userRepository.getUser();
        var savedPost = new SavedPost(author, post.getId(), type, SavedPostStatus.IN_PROGRESS, null);
        savedPostRepository.save(savedPost);
    }

    /**
     * Removes a bookmark of a post for the currently logged-in user, if post is not saved it returns
     *
     * @param post post to remove from bookmarks
     * @return false if the saved post was not found, true if post was found and deleted
     */
    public boolean removeSavedPostForCurrentUser(Posting post) {
        var existingSavedPost = this.getSavedPostForCurrentUser(post);

        if (existingSavedPost == null) {
            return false;
        }

        savedPostRepository.delete(existingSavedPost);

        return true;
    }

    /**
     * Updates the status of a bookmark, will return if no bookmark is present
     *
     * @param post   post to change status
     * @param status status to change towards
     */
    public void updateStatusOfSavedPostForCurrentUser(Posting post, SavedPostStatus status) {
        var existingSavedPost = this.getSavedPostForCurrentUser(post);

        if (existingSavedPost == null) {
            return;
        }

        existingSavedPost.setStatus(status);
        existingSavedPost.setCompletedAt(status == SavedPostStatus.IN_PROGRESS ? null : ZonedDateTime.now());
        savedPostRepository.save(existingSavedPost);
    }

    /**
     * Retrieve the saved posts for a given status
     *
     * @param status status to query
     * @return a list of all saved posts of the current user with the given status
     */
    public List<SavedPost> getSavedPostsForCurrentUserByStatus(SavedPostStatus status) {
        var currentUser = userRepository.getUser();

        return savedPostRepository.findSavedPostsByUserIdAndStatusOrderByCompletedAtDescIdDesc(currentUser.getId(), status);
    }

    /**
     * Checks if maximum amount of saved posts limit is reached
     *
     * @return true if max saved post it reached, false otherwise
     */
    public boolean isMaximumSavedPostsReached() {
        var currentUser = userRepository.getUser();

        return MAX_SAVED_POSTS_PER_USER <= savedPostRepository.countByUserId(currentUser.getId());
    }

    /**
     * Helper method to retrieve a bookmark for the current user
     *
     * @param post post to search bookmark for
     * @return The saved post for the given posting if present
     */
    private SavedPost getSavedPostForCurrentUser(Posting post) {
        PostingType type = post instanceof Post ? PostingType.POST : PostingType.ANSWER;
        var author = userRepository.getUser();

        return savedPostRepository.findSavedPostByUserIdAndPostIdAndPostType(author.getId(), post.getId(), type);
    }
}
