package de.tum.in.www1.artemis.web.rest.dto;

import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.ComplaintResponse;
import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.TextBlock;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TextAssessmentUpdateDTO(List<Feedback> feedbacks, ComplaintResponse complaintResponse, String assessmentNote, Set<TextBlock> textBlocks)
        implements AssessmentUpdateBaseDTO {
}
