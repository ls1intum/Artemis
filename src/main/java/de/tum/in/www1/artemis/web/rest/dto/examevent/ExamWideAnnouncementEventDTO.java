package de.tum.in.www1.artemis.web.rest.dto.examevent;

/**
 * A DTO for the {@link de.tum.in.www1.artemis.domain.exam.event.ExamWideAnnouncementEvent} entity.
 */
public class ExamWideAnnouncementEventDTO extends ExamLiveEventDTO {

    private String text;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    @Override
    public String getEventType() {
        return "examWideAnnouncement";
    }
}
