package de.tum.in.www1.artemis.domain.exam.quiz;

import java.util.*;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.quiz.*;
import de.tum.in.www1.artemis.service.QuizConfiguration;

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

    public QuizPool() {
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

    @Override
    public void setQuestionParent(QuizQuestion quizQuestion) {
        quizQuestion.setQuizPool(this);
    }

    @Override
    public void postReconnectJSONIgnoreAttributes() {
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
        Map<String, QuizGroup> quizGroupMap = new HashMap<>();
        for (QuizQuestion quizQuestion : quizQuestions) {
            QuizGroup quizGroup = quizQuestion.getQuizGroup();
            if (quizGroup != null) {
                quizGroupMap.put(quizGroup.getName(), quizGroup);
            }
        }
        return new ArrayList<>(quizGroupMap.values());
    }

    @JsonIgnore
    public boolean isValid() {
        for (QuizQuestion quizQuestion : getQuizQuestions()) {
            if (!quizQuestion.isValid()) {
                return false;
            }
        }
        return true;
    }
}
