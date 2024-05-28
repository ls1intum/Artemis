package de.tum.in.www1.artemis.web.rest.dto.examevent;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A DTO for the {@link de.tum.in.www1.artemis.domain.exam.event.ExamWideAnnouncementEvent} entity.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ExamWideAnnouncementEventDTO extends ExamLiveEventDTO {

    private String text;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        ExamWideAnnouncementEventDTO that = (ExamWideAnnouncementEventDTO) o;
        return Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), text);
    }
}
