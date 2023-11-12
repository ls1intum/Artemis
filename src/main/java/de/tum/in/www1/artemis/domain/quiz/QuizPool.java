package de.tum.in.www1.artemis.domain.quiz;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.persistence.Transient;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.view.QuizView;

@Entity
@Table(name = "quiz_pool")
@JsonInclude
public class QuizPool extends DomainObject implements QuizConfiguration {

    @OneToOne
    @JoinColumn(name = "exam_id", referencedColumnName = "id")
    private Exam exam;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "quiz_pool_id", referencedColumnName = "id")
    private List<QuizQuestion> quizQuestions;

    @Column(name = "max_points")
    private int maxPoints;

    @Column(name = "randomize_question_order")
    @JsonView(QuizView.Before.class)
    private Boolean randomizeQuestionOrder = false;

    @Transient
    private List<QuizGroup> quizGroups;

    public QuizPool() {
        this.quizGroups = new ArrayList<>();
        this.quizQuestions = new ArrayList<>();
    }

    public void setExam(Exam exam) {
        this.exam = exam;
    }

    public Exam getExam() {
        return exam;
    }

    public int getMaxPoints() {
        return maxPoints;
    }

    public void setMaxPoints(int maxPoints) {
        this.maxPoints = maxPoints;
    }

    public Boolean getRandomizeQuestionOrder() {
        return randomizeQuestionOrder;
    }

    public void setRandomizeQuestionOrder(Boolean randomizeQuestionOrder) {
        this.randomizeQuestionOrder = randomizeQuestionOrder;
    }

    @Override
    public void setQuestionParent(QuizQuestion quizQuestion) {
        // Do nothing since the relationship between QuizPool and QuizQuestion is already defined above.
    }

    @Override
    public List<QuizQuestion> getQuizQuestions() {
        return this.quizQuestions;
    }

    public void setQuizQuestions(List<QuizQuestion> quizQuestions) {
        this.quizQuestions = quizQuestions;
    }

    @JsonProperty(value = "quizGroups", access = JsonProperty.Access.READ_ONLY)
    public List<QuizGroup> getQuizGroups() {
        return quizGroups;
    }

    @JsonProperty(value = "quizGroups", access = JsonProperty.Access.WRITE_ONLY)
    public void setQuizGroups(List<QuizGroup> quizGroups) {
        this.quizGroups = quizGroups;
    }

    /**
     * Check if all quiz groups and questions are valid
     *
     * @return true if all quiz groups and questions are valid
     */
    @JsonIgnore
    public boolean isValid() {
        for (QuizGroup quizGroup : getQuizGroups()) {
            if (!quizGroup.isValid()) {
                return false;
            }
        }
        for (QuizQuestion quizQuestion : getQuizQuestions()) {
            if (!quizQuestion.isValid()) {
                return false;
            }
        }
        return true;
    }
}
