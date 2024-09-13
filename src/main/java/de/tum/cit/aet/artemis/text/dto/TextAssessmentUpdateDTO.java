package de.tum.cit.aet.artemis.text.dto;

import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.assessment.domain.ComplaintResponse;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.dto.AssessmentUpdateBaseDTO;
import de.tum.cit.aet.artemis.text.domain.TextBlock;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TextAssessmentUpdateDTO(List<Feedback> feedbacks, ComplaintResponse complaintResponse, String assessmentNote, Set<TextBlock> textBlocks)
        implements AssessmentUpdateBaseDTO {
}
