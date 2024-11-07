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

    public void removeSavedPostForCurrentUser(Posting post) {
        var existingSavedPost = this.getSavedPostForCurrentUser(post);

        if (existingSavedPost == null) {
            return;
        }

        savedPostRepository.delete(existingSavedPost);
    }

    public void updateStatusOfSavedPostForCurrentUser(Posting post, SavedPostStatus status) {
        var existingSavedPost = this.getSavedPostForCurrentUser(post);

        if (existingSavedPost == null) {
            return;
        }

        existingSavedPost.setStatus(status.getDatabaseKey());
        existingSavedPost.setCompletedAt(status == SavedPostStatus.IN_PROGRESS ? null : ZonedDateTime.now());
        savedPostRepository.save(existingSavedPost);
    }

    public List<SavedPost> getSavedPostsForCurrentUser(SavedPostStatus status) {
        var currentUser = userRepository.getUserWithGroupsAndAuthorities();

        return savedPostRepository.findSavedPostsByUserIdAndStatusOrderById(currentUser.getId(), status.getDatabaseKey());
    }

    public boolean isMaximumSavedPostsReached() {
        var currentUser = userRepository.getUserWithGroupsAndAuthorities();

        return MAX_SAVED_POSTS_PER_USER <= savedPostRepository.countByUserId(currentUser.getId());
    }

    private SavedPost getSavedPostForCurrentUser(Posting post) {
        PostingType type = post instanceof Post ? PostingType.POST : PostingType.ANSWER;
        var author = userRepository.getUserWithGroupsAndAuthorities();

        return savedPostRepository.findSavedPostByUserIdAndPostIdAndPostType(author.getId(), post.getId(), type.getDatabaseKey());
    }
}
