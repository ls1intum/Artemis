package de.tum.cit.aet.artemis.programming.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.exercise.dto.ParticipationDTO;
import de.tum.cit.aet.artemis.exercise.dto.SubmissionDTO;

/**
 * DTO containing Result information.
 * This does not include large reference attributes in order to send minimal data to the client.
 */
// TODO: the result should include an actual string calculated and rounded on server side (based on exercise and course settings), this should not be done on the client side
// this would also simplify the logic in result.component.ts and and result.service.ts and make the experience more consistent among different clients (webapp, ios, android)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ResultDTO(Long id, ZonedDateTime completionDate, Boolean successful, Double score, Boolean rated, SubmissionDTO submission, ParticipationDTO participation,
        List<FeedbackDTO> feedbacks, AssessmentType assessmentType, Integer correctionRound, Boolean hasComplaint, Boolean exampleResult, Integer testCaseCount,
        Integer passedTestCaseCount, Integer codeIssueCount) implements Serializable {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record FeedbackDTO(Long id, String text, String detailText, boolean hasLongFeedbackText, String reference, Double credits, Boolean positive, FeedbackType type,
            Visibility visibility, TestCaseDTO testCase) implements Serializable {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TestCaseDTO(String testName, Long id) implements Serializable {
    }

}
