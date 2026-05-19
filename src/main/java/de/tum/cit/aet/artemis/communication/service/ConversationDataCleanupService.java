package de.tum.cit.aet.artemis.communication.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.repository.PostRepository;
import de.tum.cit.aet.artemis.communication.repository.ReactionRepository;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityWeaviateService;

@Service
@Profile(PROFILE_CORE)
public class ConversationDataCleanupService {

    private final ReactionRepository reactionRepository;

    private final AnswerPostRepository answerPostRepository;

    private final PostRepository postRepository;

    private final Optional<SearchableEntityWeaviateService> searchableEntityWeaviateService;

    public ConversationDataCleanupService(ReactionRepository reactionRepository, AnswerPostRepository answerPostRepository, PostRepository postRepository,
            Optional<SearchableEntityWeaviateService> searchableEntityWeaviateService) {
        this.reactionRepository = reactionRepository;
        this.answerPostRepository = answerPostRepository;
        this.postRepository = postRepository;
        this.searchableEntityWeaviateService = searchableEntityWeaviateService;
    }

    /**
     * Deletes all conversation data (reactions, answer posts, posts) for a course while preserving
     * the conversation/channel structure. Deletion is performed in the correct order
     * (reactions -> answer posts -> posts) to handle foreign key constraints,
     * as bulk delete queries bypass JPA cascade behavior.
     *
     * @param courseId the ID of the course whose conversation data should be deleted
     */
    public void deleteAllConversationDataForCourse(long courseId) {
        reactionRepository.deleteAllByAnswerPostCourseId(courseId);
        reactionRepository.deleteAllByPostCourseId(courseId);
        answerPostRepository.deleteAllByCourseId(courseId);
        postRepository.deleteAllByCourseId(courseId);
        searchableEntityWeaviateService.ifPresent(service -> service.deleteAllPostsForCourseAsync(courseId));
    }
}
