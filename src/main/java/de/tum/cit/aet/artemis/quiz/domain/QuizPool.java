package de.tum.cit.aet.artemis.quiz.domain;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;

import de.tum.cit.aet.artemis.domain.DomainObject;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.quiz.config.QuizView;

@Entity
@Table(name = "quiz_pool")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
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
    // NOTE: there is no guarantee for a specific order because the underlying data is stored in a set without order column
    public List<QuizQuestion> getQuizQuestions() {
        return this.quizQuestions;
    }

    public void setQuizQuestions(List<QuizQuestion> quizQuestions) {
        this.quizQuestions = quizQuestions;
    }

    @JsonProperty(value = "quizGroups", access = JsonProperty.Access.READ_ONLY)
    // NOTE: there is no guarantee for a specific order because the underlying data is stored in a set without order column
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
