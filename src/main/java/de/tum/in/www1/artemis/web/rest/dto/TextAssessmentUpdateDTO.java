package de.tum.in.www1.artemis.web.rest.dto;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.in.www1.artemis.domain.ComplaintResponse;
import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.TextBlock;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TextAssessmentUpdateDTO(List<Feedback> feedbacks, ComplaintResponse complaintResponse, String assessmentNote, Set<TextBlock> textBlocks) implements AssessmentUpdate {

    /**
     * This is the constructor for the TextAssessmentUpdateDTO used during deserialization. It ensures that collections are non-null.
     *
     * @param feedbacks         A list of feedbacks. If the provided list is null, an empty ArrayList is used instead.
     * @param complaintResponse A complaint response.
     * @param assessmentNote    A note for the assessment.
     * @param textBlocks        A set of text blocks. If the provided set is null, an empty HashSet is used instead.
     */
    @JsonCreator
    public TextAssessmentUpdateDTO(@JsonProperty("feedbacks") List<Feedback> feedbacks, @JsonProperty("complaintResponse") ComplaintResponse complaintResponse,
            @JsonProperty("assessmentNote") String assessmentNote, @JsonProperty("textBlocks") Set<TextBlock> textBlocks) {
        this.feedbacks = Optional.ofNullable(feedbacks).orElseGet(ArrayList::new);
        this.complaintResponse = complaintResponse;
        this.assessmentNote = assessmentNote;
        this.textBlocks = Optional.ofNullable(textBlocks).orElseGet(HashSet::new);
    }
}
