package de.tum.cit.aet.artemis.communication.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.Posting;
import de.tum.cit.aet.artemis.communication.domain.PostingType;
import de.tum.cit.aet.artemis.communication.domain.SavedPost;
import de.tum.cit.aet.artemis.communication.domain.SavedPostStatus;
import de.tum.cit.aet.artemis.communication.dto.PostingDTO;
import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.repository.PostRepository;
import de.tum.cit.aet.artemis.communication.service.SavedPostService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.util.TimeLogUtil;

/**
 * REST controller for managing Message Posts.
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/communication/")
public class SavedPostResource {

    private static final Logger log = LoggerFactory.getLogger(SavedPostResource.class);

    public static final String ENTITY_NAME = "savedPost";

    private final SavedPostService savedPostService;

    private final PostRepository postRepository;

    private final AnswerPostRepository answerPostRepository;

    private final UserRepository userRepository;

    public SavedPostResource(SavedPostService savedPostService, PostRepository postRepository, AnswerPostRepository answerPostRepository, UserRepository userRepository) {
        this.savedPostService = savedPostService;
        this.postRepository = postRepository;
        this.answerPostRepository = answerPostRepository;
        this.userRepository = userRepository;
    }

    /**
     * GET /saved-posts : Get saved posts of the course for the logged in user with specific status
     *
     * @param courseId id of course to filter posts
     * @param status   saved post status (progress, completed, archived)
     * @return ResponseEntity with status 200 (Success) if course id and status are ok,
     *         or with status 400 (Bad Request) if the checks on type or post validity fail
     */
    @GetMapping("saved-posts")
    @EnforceAtLeastStudent
    public ResponseEntity<List<PostingDTO>> getSavedPosts(@RequestParam Long courseId, @RequestParam(name = "status") String status) {
        SavedPostStatus savedPostStatus = SavedPostStatus.fromString(status);
        log.debug("GET getSavedPosts invoked for course {} and status {}", courseId, savedPostStatus);
        long start = System.nanoTime();

        var savedPosts = savedPostService.getSavedPostsForCurrentUserByStatus(savedPostStatus);

        List<Post> posts = postRepository.findByIdIn(savedPosts.stream().filter(savedPost -> savedPost.getPostType() == PostingType.POST).map(SavedPost::getPostId).toList())
                .stream().filter(post -> Objects.equals(post.getCoursePostingBelongsTo().getId(), courseId)).toList();
        List<AnswerPost> answerPosts = answerPostRepository
                .findByIdIn(savedPosts.stream().filter(savedPost -> savedPost.getPostType() == PostingType.ANSWER).map(SavedPost::getPostId).toList()).stream()
                .filter(post -> Objects.equals(post.getCoursePostingBelongsTo().getId(), courseId)).toList();
        List<PostingDTO> postingList = new ArrayList<>();

        for (SavedPost savedPost : savedPosts) {
            Optional<? extends Posting> optionalPosting;
            if (savedPost.getPostType() == PostingType.ANSWER) {
                optionalPosting = answerPosts.stream().filter(answerPost -> answerPost.getId().equals(savedPost.getPostId())).findFirst();
            }
            else {
                optionalPosting = posts.stream().filter(post -> post.getId().equals(savedPost.getPostId())).findFirst();
            }
            optionalPosting.ifPresent(posting -> postingList.add(new PostingDTO(posting, true, savedPost.getStatus())));
        }

        log.info("getSavedPosts took {}", TimeLogUtil.formatDurationFrom(start));
        return new ResponseEntity<>(postingList, null, HttpStatus.OK);
    }

    /**
     * POST /saved-posts/{postId} : Create a new saved post
     *
     * @param postId post to save
     * @param type   post type (post, answer)
     * @return ResponseEntity with status 201 (Created) if successfully saved post,
     *         or with status 400 (Bad Request) if the checks on type or post validity fail
     */
    @PostMapping("saved-posts/{postId}")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> savePost(@PathVariable Long postId, @RequestParam(name = "type") String type) {
        PostingType postingType = PostingType.fromString(type);
        log.debug("POST savePost invoked for post {}", postId);
        long start = System.nanoTime();

        if (savedPostService.isMaximumSavedPostsReached()) {
            throw new BadRequestAlertException("The maximum amount of saved posts was reached.", ENTITY_NAME, "savedPostMaxReached");
        }

        User user = userRepository.getUser();
        // authorization checks: we need to verify that the user has access to the postings with the given IDs in postingIds
        // this is the case if the post is in a course wide channel or if the user is part of the OneToOne / Channel
        switch (postingType) {
            case POST -> postRepository.userHasAccessToAllPostsElseThrow(Collections.singleton(postId), user.getId());
            case ANSWER -> answerPostRepository.userHasAccessToAllAnswerPostsElseThrow(Collections.singleton(postId), user.getId());
        }

        var post = retrievePostingElseThrow(postId, postingType);

        savedPostService.savePostForCurrentUser(post);

        log.info("savePost took {}", TimeLogUtil.formatDurationFrom(start));
        return new ResponseEntity<>(null, null, HttpStatus.CREATED);
    }

    /**
     * DELETE /saved-posts/{postId} : Remove a saved post
     *
     * @param postId post to save
     * @param type   post type (post, answer)
     * @return ResponseEntity with status 204 (No content) if successfully deleted post,
     *         or with status 400 (Bad Request) if the checks on type or post validity fail
     */
    @DeleteMapping("saved-posts/{postId}")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> deleteSavedPost(@PathVariable Long postId, @RequestParam(name = "type") String type) {
        PostingType postingType = PostingType.fromString(type);
        log.debug("DELETE deletePost invoked for post {}", postId);
        long start = System.nanoTime();

        // the user should only be able to delete their own saved posts, this is checked in removeSavedPostForCurrentUser

        var posting = retrievePostingElseThrow(postId, postingType);

        if (!savedPostService.removeSavedPostForCurrentUser(posting)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not allowed to delete this bookmark.");
        }

        log.info("deletePost took {}", TimeLogUtil.formatDurationFrom(start));
        return new ResponseEntity<>(null, null, HttpStatus.NO_CONTENT);
    }

    /**
     * PUT /saved-posts/{postId}/{type} : Update the status of a saved post
     *
     * @param postId post to save
     * @param type   post type (post, answer)
     * @param status saved post status (progress, answer)
     * @return ResponseEntity with status 200 (Success) if successfully updated saved post status,
     *         or with status 400 (Bad Request) if the checks on type or post validity fail
     */
    @PutMapping("saved-posts/{postId}")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> putSavedPost(@PathVariable Long postId, @RequestParam(name = "type") String type, @RequestParam(name = "status") String status) {
        PostingType postingType = PostingType.fromString(type);
        SavedPostStatus savedPostStatus = SavedPostStatus.fromString(status);
        log.debug("DELETE putSavedPost invoked for post {}", postId);
        long start = System.nanoTime();

        var posting = retrievePostingElseThrow(postId, postingType);

        savedPostService.updateStatusOfSavedPostForCurrentUser(posting, savedPostStatus);

        log.info("putSavedPost took {}", TimeLogUtil.formatDurationFrom(start));
        return new ResponseEntity<>(null, null, HttpStatus.OK);
    }

    private Posting retrievePostingElseThrow(long postId, PostingType postingType) throws BadRequestAlertException {
        Posting posting;
        try {
            if (postingType == PostingType.POST) {
                posting = postRepository.findPostOrMessagePostByIdElseThrow(postId);
            }
            else {
                posting = answerPostRepository.findAnswerPostOrMessageByIdElseThrow(postId);
            }
        }
        catch (EntityNotFoundException e) {
            log.error("Could not find post with id {} and type {}", postId, postingType, e);
            throw new BadRequestAlertException("The provided post could not be found.", ENTITY_NAME, "savedPostIdDoesNotExist");
        }

        return posting;
    }
}
