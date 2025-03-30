package de.tum.cit.aet.artemis.communication.dto;

import java.time.ZonedDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.Posting;
import de.tum.cit.aet.artemis.communication.domain.PostingType;
import de.tum.cit.aet.artemis.communication.domain.SavedPostStatus;
import de.tum.cit.aet.artemis.communication.domain.UserRole;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PostingDTO(Long id, AuthorDTO author, UserRole role, ZonedDateTime creationDate, ZonedDateTime updatedDate, String content, boolean isSaved,
        SavedPostStatus savedPostStatus, List<ReactionDTO> reactions, PostingConversationDTO conversation, PostingType postingType, Long referencePostId) {

    public PostingDTO(Posting post, boolean isSaved, SavedPostStatus savedPostStatus) {
        this(post.getId(), new AuthorDTO(post.getAuthor()), post.getAuthorRole(), post.getCreationDate(), post.getUpdatedDate(), post.getContent(), isSaved, savedPostStatus,
                post.getReactions().stream().map(ReactionDTO::new).toList(), new PostingConversationDTO(post.getConversation()), getSavedPostType(post), getReferencePostId(post));
    }

    static PostingType getSavedPostType(Posting posting) {
        if (posting instanceof AnswerPost) {
            return PostingType.ANSWER;
        }
        else {
            return PostingType.POST;
        }
    }

    static Long getReferencePostId(Posting posting) {
        if (posting instanceof AnswerPost) {
            return ((AnswerPost) posting).getPost().getId();
        }
        else {
            return posting.getId();
        }
    }
}
