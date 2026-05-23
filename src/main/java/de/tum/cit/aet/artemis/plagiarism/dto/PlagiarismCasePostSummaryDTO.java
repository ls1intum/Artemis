package de.tum.cit.aet.artemis.plagiarism.dto;

import java.time.ZonedDateTime;
import java.util.List;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.Post;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PlagiarismCasePostSummaryDTO(Long id, ZonedDateTime creationDate, List<Long> answerAuthorIds) {

    public static PlagiarismCasePostSummaryDTO fromPost(Post post) {
        if (post == null) {
            return null;
        }

        List<Long> answerAuthorIds = null;
        if (post.getAnswers() != null && Hibernate.isInitialized(post.getAnswers())) {
            answerAuthorIds = post.getAnswers().stream().filter(answer -> answer.getAuthor() != null).map(answer -> answer.getAuthor().getId()).toList();
        }

        return new PlagiarismCasePostSummaryDTO(post.getId(), post.getCreationDate(), answerAuthorIds);
    }
}
