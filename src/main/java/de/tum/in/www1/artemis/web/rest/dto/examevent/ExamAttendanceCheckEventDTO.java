package de.tum.in.www1.artemis.web.rest.dto.examevent;

import java.util.Objects;

/**
 * A DTO for the {@link de.tum.in.www1.artemis.domain.exam.event.ExamAttendanceCheckEvent} entity.
 */
public class ExamAttendanceCheckEventDTO extends ExamLiveEventDTO {

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
        ExamAttendanceCheckEventDTO that = (ExamAttendanceCheckEventDTO) o;
        return Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), text);
    }
}
