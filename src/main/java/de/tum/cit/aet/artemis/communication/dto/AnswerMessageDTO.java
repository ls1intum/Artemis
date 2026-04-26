package de.tum.cit.aet.artemis.communication.dto;

import java.time.ZonedDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.UserRole;
import de.tum.cit.aet.artemis.core.domain.User;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AnswerMessageDTO(Long id, AuthorDTO author, UserRole authorRole, ZonedDateTime creationDate, ZonedDateTime updatedDate, String content, boolean isSaved,
        List<ReactionDTO> reactions, Boolean resolvesPost, Double confidenceScore, boolean verified, VerifiedByDTO verifiedBy, ZonedDateTime verifiedAt, ParentPostDTO post) {

    public AnswerMessageDTO(AnswerPost answerPost) {
        this(answerPost.getId(), AuthorDTO.fromUser(answerPost.getAuthor()), answerPost.getAuthorRole(), answerPost.getCreationDate(), answerPost.getUpdatedDate(),
                answerPost.getContent(), answerPost.getIsSaved(), answerPost.getReactions().stream().map(ReactionDTO::new).toList(), answerPost.doesResolvePost(),
                answerPost.getConfidenceScore(), answerPost.isVerified(), VerifiedByDTO.fromUser(answerPost.getVerifiedBy()), answerPost.getVerifiedAt(),
                answerPost.getPost() != null ? new ParentPostDTO(answerPost.getPost().getId()) : null);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record VerifiedByDTO(Long id, String login, String firstName, String lastName) {

        public static VerifiedByDTO fromUser(User user) {
            return user == null ? null : new VerifiedByDTO(user.getId(), user.getLogin(), user.getFirstName(), user.getLastName());
        }
    }
}
