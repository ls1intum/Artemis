package de.tum.in.www1.artemis.web.rest.dto;

import java.time.ZonedDateTime;
import java.util.Objects;

import de.tum.in.www1.artemis.domain.exam.Exam;

public class ExamDTO {

    public Long id;

    public ZonedDateTime startDate;

    public ZonedDateTime endDate;

    public ZonedDateTime visibleDate;

    public String startText;

    public String endText;

    public String confirmationStartText;

    public String confirmationEndText;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ZonedDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(ZonedDateTime startDate) {
        this.startDate = startDate;
    }

    public ZonedDateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(ZonedDateTime endDate) {
        this.endDate = endDate;
    }

    public ZonedDateTime getVisibleDate() {
        return visibleDate;
    }

    public void setVisibleDate(ZonedDateTime visibleDate) {
        this.visibleDate = visibleDate;
    }

    public String getStartText() {
        return startText;
    }

    public void setStartText(String startText) {
        this.startText = startText;
    }

    public String getEndText() {
        return endText;
    }

    public void setEndText(String endText) {
        this.endText = endText;
    }

    public String getConfirmationStartText() {
        return confirmationStartText;
    }

    public void setConfirmationStartText(String confirmationStartText) {
        this.confirmationStartText = confirmationStartText;
    }

    public String getConfirmationEndText() {
        return confirmationEndText;
    }

    public void setConfirmationEndText(String confirmationEndText) {
        this.confirmationEndText = confirmationEndText;
    }

    public static ExamDTO createFromEntity(Exam exam) {
        ExamDTO examDTO = new ExamDTO();
        examDTO.setId(exam.getId());
        examDTO.setStartDate(exam.getStartDate());
        examDTO.setEndDate(exam.getEndDate());
        examDTO.setVisibleDate(exam.getVisibleDate());
        examDTO.setStartText(exam.getStartText());
        examDTO.setEndText(exam.getEndText());
        examDTO.setConfirmationStartText(exam.getConfirmationStartText());
        examDTO.setConfirmationEndText(exam.getEndText());
        return examDTO;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ExamDTO examDTO = (ExamDTO) o;
        return Objects.equals(id, examDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "ExamDTO{" + "id=" + id + ", startDate=" + startDate + ", endDate=" + endDate + ", visibleDate=" + visibleDate + ", startText='" + startText + '\'' + ", endText='"
                + endText + '\'' + ", confirmationStartText='" + confirmationStartText + '\'' + ", confirmationEndText='" + confirmationEndText + '\'' + '}';
    }
}
