package de.tum.in.www1.artemis.domain.exam.statistics;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonManagedReference;

import de.tum.in.www1.artemis.domain.DomainObject;

/**
 * In order to extend the actions in the future, this ExamActivity serves as a container of actions performed per student.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ExamActivity extends DomainObject {

    /**
     * Since we want to avoid DTOs (and avoid sending the complete exam object),
     * we only keep the corresponding student exam id.
     */
    private Long studentExamId;

    /**
     * A set of unique actions performed by the student during the exam.
     */
    @JsonManagedReference
    private final List<ExamAction> examActions = new ArrayList<>();

    public Long getStudentExamId() {
        return studentExamId;
    }

    public void setStudentExamId(Long studentExamId) {
        this.studentExamId = studentExamId;
    }

    public void addExamAction(ExamAction examAction) {
        this.examActions.add(examAction);
    }

    public void addExamActions(List<ExamAction> examActions) {
        this.examActions.addAll(examActions);
    }

    public List<ExamAction> getExamActions() {
        return examActions;
    }

    @Override
    public String toString() {
        return "ExamActivity{" + "studentExamId=" + studentExamId + '}';
    }
}
