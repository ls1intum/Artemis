package de.tum.in.www1.artemis.domain.exam.event;

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

import de.tum.in.www1.artemis.web.rest.dto.examevent.ExamAttendanceCheckEventDTO;

/**
 * An event indicating an updated working time for a specific student exam.
 */
@Entity
@DiscriminatorValue(value = "W")
public class ExamAttendanceCheckEvent extends ExamLiveEvent {

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
    public ExamAttendanceCheckEventDTO asDTO() {
        var dto = new ExamAttendanceCheckEventDTO();
        super.populateDTO(dto);
        dto.setText(textContent);
        return dto;
    }
}
