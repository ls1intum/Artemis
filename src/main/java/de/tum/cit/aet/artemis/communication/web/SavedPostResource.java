package de.tum.cit.aet.artemis.communication.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.util.TimeLogUtil;

/**
 * REST controller for managing Message Posts.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class SavedPostResource {

    private static final Logger log = LoggerFactory.getLogger(SavedPostResource.class);

    public static final String ENTITY_NAME = "savedPost";

    private final SavedPostService savedPostService;

    private final PostRepository postRepository;

    private final AnswerPostRepository answerPostRepository;

    public SavedPostResource(SavedPostService savedPostService, PostRepository postRepository, AnswerPostRepository answerPostRepository) {
        this.savedPostService = savedPostService;
        this.postRepository = postRepository;
        this.answerPostRepository = answerPostRepository;
    }

    /**
     * GET /saved-posts/{courseId}/{status} : Get saved posts of course with specific status
     *
     * @param courseId id of course to filter posts
     * @param status   saved post status (progress, completed, archived)
     * @return ResponseEntity with status 200 (Success) if course id and status are ok,
     *         or with status 400 (Bad Request) if the checks on type or post validity fail
     */
    @GetMapping("saved-posts/{courseId}/{status}")
    @EnforceAtLeastStudent
    public ResponseEntity<List<PostingDTO>> getSavedPosts(@PathVariable Long courseId, @PathVariable short status) {
        log.debug("GET getSavedPosts invoked for course {} and status {}", courseId, status);
        long start = System.nanoTime();

        SavedPostStatus savedPostStatus;
        try {
            savedPostStatus = SavedPostStatus.fromDatabaseKey(status);
        }
        catch (IllegalArgumentException e) {
            throw new BadRequestAlertException("The provided post status could not be found.", ENTITY_NAME, "savedPostStatusDoesNotExist");
        }

        var savedPosts = savedPostService.getSavedPostsForCurrentUserByStatus(savedPostStatus);

        List<Post> posts = postRepository.findByIdIn(savedPosts.stream().filter(savedPost -> savedPost.getPostType() == PostingType.POST).map(SavedPost::getPostId).toList())
                .stream().filter(post -> Objects.equals(post.getCoursePostingBelongsTo().getId(), courseId)).toList();
        List<AnswerPost> answerPosts = answerPostRepository
                .findByIdIn(savedPosts.stream().filter(savedPost -> savedPost.getPostType() == PostingType.ANSWER).map(SavedPost::getPostId).toList()).stream()
                .filter(post -> Objects.equals(post.getCoursePostingBelongsTo().getId(), courseId)).toList();
        List<PostingDTO> postingList = new ArrayList<>();

        for (SavedPost savedPost : savedPosts) {
            Optional posting;
            if (savedPost.getPostType() == PostingType.ANSWER) {
                posting = answerPosts.stream().filter(answerPost -> answerPost.getId().equals(savedPost.getPostId())).findFirst();
            }
            else {
                posting = posts.stream().filter(post -> post.getId().equals(savedPost.getPostId())).findFirst();
            }
            if (posting.isPresent()) {
                postingList.add(new PostingDTO((Posting) posting.get(), true, savedPost.getStatus().getDatabaseKey()));
            }
        }

        log.info("getSavedPosts took {}", TimeLogUtil.formatDurationFrom(start));
        return new ResponseEntity<>(postingList, null, HttpStatus.OK);
    }

    /**
     * POST /saved-posts/{postId}/{type} : Create a new saved post
     *
     * @param postId post to save
     * @param type   post type (post, answer)
     * @return ResponseEntity with status 201 (Created) if successfully saved post,
     *         or with status 400 (Bad Request) if the checks on type or post validity fail
     */
    @PostMapping("saved-posts/{postId}/{type}")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> savePost(@PathVariable Long postId, @PathVariable short type) {
        log.debug("POST savePost invoked for post {}", postId);
        long start = System.nanoTime();

        if (savedPostService.isMaximumSavedPostsReached()) {
            throw new BadRequestAlertException("The maximum amount of saved posts was reached.", ENTITY_NAME, "savedPostMaxReached");
        }

        var post = retrievePostingElseThrow(postId, type);

        this.savedPostService.savePostForCurrentUser(post);

        log.info("savePost took {}", TimeLogUtil.formatDurationFrom(start));
        return new ResponseEntity<>(null, null, HttpStatus.CREATED);
    }

    /**
     * DELETE /saved-posts/{postId}/{type} : Remove a saved post
     *
     * @param postId post to save
     * @param type   post type (post, answer)
     * @return ResponseEntity with status 204 (No content) if successfully deleted post,
     *         or with status 400 (Bad Request) if the checks on type or post validity fail
     */
    @DeleteMapping("saved-posts/{postId}/{type}")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> deleteSavedPost(@PathVariable Long postId, @PathVariable short type) {
        log.debug("DELETE deletePost invoked for post {}", postId);
        long start = System.nanoTime();

        var post = retrievePostingElseThrow(postId, type);

        if (!this.savedPostService.removeSavedPostForCurrentUser(post)) {
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
    @PutMapping("saved-posts/{postId}/{type}")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> putSavedPost(@PathVariable Long postId, @PathVariable short type, @RequestParam(name = "status") short status) {
        log.debug("DELETE putSavedPost invoked for post {}", postId);
        long start = System.nanoTime();

        var post = retrievePostingElseThrow(postId, type);

        SavedPostStatus savedPostStatus;
        try {
            savedPostStatus = SavedPostStatus.fromDatabaseKey(status);
        }
        catch (IllegalArgumentException e) {
            throw new BadRequestAlertException("The provided post status could not be found.", ENTITY_NAME, "savedPostStatusDoesNotExist");
        }

        this.savedPostService.updateStatusOfSavedPostForCurrentUser(post, savedPostStatus);

        log.info("putSavedPost took {}", TimeLogUtil.formatDurationFrom(start));
        return new ResponseEntity<>(null, null, HttpStatus.OK);
    }

    private Posting retrievePostingElseThrow(long postId, short type) throws BadRequestAlertException {
        PostingType postingType;

        try {
            postingType = PostingType.fromDatabaseKey(type);
        }
        catch (IllegalArgumentException e) {
            throw new BadRequestAlertException("The provided post type could not be found.", ENTITY_NAME, "savedPostTypeDoesNotExist");
        }

        Posting post;
        try {
            if (postingType == PostingType.POST) {
                post = postRepository.findPostOrMessagePostByIdElseThrow(postId);
            }
            else {
                post = answerPostRepository.findAnswerPostOrMessageByIdElseThrow(postId);
            }
        }
        catch (EntityNotFoundException e) {
            throw new BadRequestAlertException("The provided post could not be found.", ENTITY_NAME, "savedPostIdDoesNotExist");
        }

        return post;
    }
}
