package de.tum.cit.aet.artemis.web.rest.dto;

import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.domain.ComplaintResponse;
import de.tum.cit.aet.artemis.domain.Feedback;
import de.tum.cit.aet.artemis.domain.TextBlock;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TextAssessmentUpdateDTO(List<Feedback> feedbacks, ComplaintResponse complaintResponse, String assessmentNote, Set<TextBlock> textBlocks)
        implements AssessmentUpdateBaseDTO {
}
