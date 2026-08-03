package de.tum.cit.aet.artemis.communication.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.repository.ConversationParticipantRepository;
import de.tum.cit.aet.artemis.communication.repository.PostRepository;
import de.tum.cit.aet.artemis.communication.repository.ReactionRepository;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityWeaviateService;

@Lazy
@Service
@Profile(PROFILE_CORE)
public class ConversationDataCleanupService {

    private final ReactionRepository reactionRepository;

    private final AnswerPostRepository answerPostRepository;

    private final PostRepository postRepository;

    private final ConversationParticipantRepository conversationParticipantRepository;

    private final Optional<SearchableEntityWeaviateService> searchableEntityWeaviateService;

    public ConversationDataCleanupService(ReactionRepository reactionRepository, AnswerPostRepository answerPostRepository, PostRepository postRepository,
            ConversationParticipantRepository conversationParticipantRepository, Optional<SearchableEntityWeaviateService> searchableEntityWeaviateService) {
        this.reactionRepository = reactionRepository;
        this.answerPostRepository = answerPostRepository;
        this.postRepository = postRepository;
        this.conversationParticipantRepository = conversationParticipantRepository;
        this.searchableEntityWeaviateService = searchableEntityWeaviateService;
    }

    /**
     * Deletes all conversation data (reactions, answer posts, posts, and per-user participant/membership rows) for a
     * course while preserving the conversation/channel structure. Post deletion is performed in the correct order
     * (reactions -> answer posts -> posts) to handle foreign key constraints, as bulk delete queries bypass JPA cascade
     * behavior. The participant rows (channel membership and read state) are removed so no student communication data
     * remains; the (empty) channels themselves are kept for reuse.
     *
     * @param courseId the ID of the course whose conversation data should be deleted
     */
    public void deleteAllConversationDataForCourse(long courseId) {
        reactionRepository.deleteAllByAnswerPostCourseId(courseId);
        reactionRepository.deleteAllByPostCourseId(courseId);
        answerPostRepository.deleteAllByCourseId(courseId);
        postRepository.deleteAllByCourseId(courseId);
        conversationParticipantRepository.deleteAllByConversationCourseId(courseId);
        searchableEntityWeaviateService.ifPresent(service -> service.deleteAllPostsForCourseAsync(courseId));
    }
}
