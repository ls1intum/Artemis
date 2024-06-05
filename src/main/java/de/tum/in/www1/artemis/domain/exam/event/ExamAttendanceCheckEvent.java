package de.tum.in.www1.artemis.domain.exam.event;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import de.tum.in.www1.artemis.web.rest.dto.examevent.ExamAttendanceCheckEventDTO;

/**
 * An event indicating an attendance check trigger during an exam.
 */
@Entity
@DiscriminatorValue(value = "C")
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
        return new ExamAttendanceCheckEventDTO(this.getId(), this.getCreatedBy(), this.getCreatedDate(), textContent);
    }
}
