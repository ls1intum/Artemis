package de.tum.in.www1.artemis.domain.exam.event;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import de.tum.in.www1.artemis.web.rest.dto.examevent.ExamWideAnnouncementEventDTO;

/**
 * An exam wide announcement event.
 */
@Entity
@DiscriminatorValue(value = "A")
public class ExamWideAnnouncementEvent extends ExamLiveEvent {

    /**
     * The text content of the announcement.
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
    public ExamWideAnnouncementEventDTO asDTO() {
        return new ExamWideAnnouncementEventDTO(this.getId(), this.getCreatedBy(), this.getCreatedDate(), textContent);
    }
}
