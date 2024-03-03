package de.tum.in.www1.artemis.domain.exam.event;

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

import de.tum.in.www1.artemis.web.rest.dto.examevent.ProblemStatementUpdateEventDTO;

/**
 * An event indicating an update of the problem statement of an exercise during an exam.
 */
@Entity
@DiscriminatorValue(value = "P")
public class ProblemStatementUpdateEvent extends ExamLiveEvent {

    /**
     * optional text content of the instructor.
     */
    @Column(name = "textContent")
    private String textContent;

    public String getTextContent() {
        return textContent;
    }

    public void setTextContent(String textContent) {
        this.textContent = textContent;
    }

    @Override
    public ProblemStatementUpdateEventDTO asDTO() {
        var dto = new ProblemStatementUpdateEventDTO();
        super.populateDTO(dto);
        dto.setText(textContent);
        return dto;
    }
}
