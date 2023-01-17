package de.tum.in.www1.artemis.domain.exam.quiz;

import java.util.Set;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.quiz.QuizQuestion;

@Entity
@Table(name = "quiz_group")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class QuizGroup extends DomainObject {

    @Column(name = "name")
    private String name;

    @OneToMany(mappedBy = "quizGroup")
    @JsonIgnore
    private Set<QuizQuestion> quizQuestions;

    public QuizGroup() {
    }

    public QuizGroup(String name) {
        setName(name);
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Set<QuizQuestion> getQuizQuestions() {
        return quizQuestions;
    }

    public void setQuizQuestions(Set<QuizQuestion> quizQuestions) {
        this.quizQuestions = quizQuestions;
    }

    @JsonIgnore
    public boolean isValid() {
        return getName() != null && !getName().isEmpty();
    }
}
