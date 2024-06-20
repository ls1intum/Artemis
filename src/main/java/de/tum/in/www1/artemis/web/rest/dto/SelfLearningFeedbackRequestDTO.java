package de.tum.in.www1.artemis.web.rest.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.List;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.SelfLearningFeedbackRequest;

/**
 * DTO containing {@link de.tum.in.www1.artemis.domain.SelfLearningFeedbackRequest} information.
 * This does not include large reference attributes in order to send minimal data to the client.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SelfLearningFeedbackRequestDTO(Long id, ZonedDateTime requestDateTime, ZonedDateTime responseDateTime, ParticipationDTO participation, ResultDTO result,
        SubmissionDTO submission, Boolean successful) implements Serializable {

    /**
     * Converts a SelfLearningFeedbackRequest into a ResultDTO
     *
     * @param selfLearningFeedbackRequest to convert
     * @param feedbacks                   to convert
     * @return the converted DTO
     */
    public static SelfLearningFeedbackRequestDTO of(SelfLearningFeedbackRequest selfLearningFeedbackRequest, List<Feedback> feedbacks) {
        SubmissionDTO submissionDTO = null;
        if (Hibernate.isInitialized(selfLearningFeedbackRequest.getSubmission()) && selfLearningFeedbackRequest.getSubmission() != null) {
            submissionDTO = SubmissionDTO.of(selfLearningFeedbackRequest.getSubmission());
        }
        ResultDTO resultDTO = null;
        if (selfLearningFeedbackRequest.getResult() != null) {
            resultDTO = ResultDTO.of(selfLearningFeedbackRequest.getResult(), feedbacks);
        }
        return new SelfLearningFeedbackRequestDTO(selfLearningFeedbackRequest.getId(), selfLearningFeedbackRequest.getRequestDateTime(),
                selfLearningFeedbackRequest.getResponseDateTime(), ParticipationDTO.of(selfLearningFeedbackRequest.getSubmission().getParticipation()), resultDTO, submissionDTO,
                selfLearningFeedbackRequest.isSuccessful());
    }

    /**
     * Converts a SelfLearningFeedbackRequest into a ResultDTO
     *
     * @param selfLearningFeedbackRequest to convert
     * @return the converted DTO
     */
    public static SelfLearningFeedbackRequestDTO of(SelfLearningFeedbackRequest selfLearningFeedbackRequest) {
        SubmissionDTO submissionDTO = null;
        if (Hibernate.isInitialized(selfLearningFeedbackRequest.getSubmission()) && selfLearningFeedbackRequest.getSubmission() != null) {
            submissionDTO = SubmissionDTO.of(selfLearningFeedbackRequest.getSubmission());
        }
        ResultDTO resultDTO = null;
        if (selfLearningFeedbackRequest.getResult() != null) {
            resultDTO = ResultDTO.of(selfLearningFeedbackRequest.getResult());
        }
        return new SelfLearningFeedbackRequestDTO(selfLearningFeedbackRequest.getId(), selfLearningFeedbackRequest.getRequestDateTime(),
                selfLearningFeedbackRequest.getResponseDateTime(), ParticipationDTO.of(selfLearningFeedbackRequest.getSubmission().getParticipation()), resultDTO, submissionDTO,
                selfLearningFeedbackRequest.isSuccessful());
    }
}
