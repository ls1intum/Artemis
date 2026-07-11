package de.tum.cit.aet.artemis.exam.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exam.domain.Exam;

/**
 * Minimal exam reference carrying only the id and title. Used where a response needs to point at an exam without
 * exposing (or forcing the load of) its full graph, e.g. the result of an exam import, where the client only navigates
 * to the imported exam by its id.
 *
 * @param id    the id of the exam
 * @param title the title of the exam
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamIdAndTitleDTO(Long id, String title) {

    /**
     * Creates a slim {@link ExamIdAndTitleDTO} from an exam.
     *
     * @param exam the exam to project
     * @return the slim DTO, or {@code null} if the exam is {@code null}
     */
    public static ExamIdAndTitleDTO of(Exam exam) {
        if (exam == null) {
            return null;
        }
        return new ExamIdAndTitleDTO(exam.getId(), exam.getTitle());
    }
}
