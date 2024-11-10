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
        var author = userRepository.getUserWithGroupsAndAuthorities();
        var savedPost = new SavedPost(author, post.getId(), type.getDatabaseKey(), SavedPostStatus.IN_PROGRESS.getDatabaseKey(), null);
        savedPostRepository.save(savedPost);
    }

    /**
     * Removes a bookmark of a post for the currently logged-in user, if post is not saved it returns
     *
     * @param post post to remove from bookmarks
     */
    public void removeSavedPostForCurrentUser(Posting post) {
        var existingSavedPost = this.getSavedPostForCurrentUser(post);

        if (existingSavedPost == null) {
            return;
        }

        savedPostRepository.delete(existingSavedPost);
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

        existingSavedPost.setStatus(status.getDatabaseKey());
        existingSavedPost.setCompletedAt(status == SavedPostStatus.IN_PROGRESS ? null : ZonedDateTime.now());
        savedPostRepository.save(existingSavedPost);
    }

    /**
     * Retrieve the saved posts for a given status
     *
     * @param status status to query
     */
    public List<SavedPost> getSavedPostsForCurrentUser(SavedPostStatus status) {
        var currentUser = userRepository.getUserWithGroupsAndAuthorities();

        return savedPostRepository.findSavedPostsByUserIdAndStatusOrderById(currentUser.getId(), status.getDatabaseKey());
    }

    /**
     * Checks if maximum amount of saved posts limit is reached
     */
    public boolean isMaximumSavedPostsReached() {
        var currentUser = userRepository.getUserWithGroupsAndAuthorities();

        return MAX_SAVED_POSTS_PER_USER <= savedPostRepository.countByUserId(currentUser.getId());
    }

    /**
     * Helper method to retrieve a bookmark for the current user
     *
     * @param post post to search bookmark for
     */
    private SavedPost getSavedPostForCurrentUser(Posting post) {
        PostingType type = post instanceof Post ? PostingType.POST : PostingType.ANSWER;
        var author = userRepository.getUserWithGroupsAndAuthorities();

        return savedPostRepository.findSavedPostByUserIdAndPostIdAndPostType(author.getId(), post.getId(), type.getDatabaseKey());
    }
}
