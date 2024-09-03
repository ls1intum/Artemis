package de.tum.in.www1.artemis.domain;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A FAQ.
 */
@Entity
@Table(name = "faq")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Faq extends DomainObject {

    @Column(name = "question_title")
    private String questionTitle;

    @Column(name = "question_answer")
    private String questionAnswer;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "faq_categories", joinColumns = @JoinColumn(name = "faq_id"))
    @Column(name = "categories")
    private Set<String> categories = new HashSet<>();

    @ManyToOne
    @JsonIgnoreProperties(value = { "faqs" }, allowSetters = true)
    private Course course;

    public String getQuestionTitle() {
        return questionTitle;
    }

    public void setQuestionTitle(String questionTitle) {
        this.questionTitle = questionTitle;
    }

    public String getQuestionAnswer() {
        return questionAnswer;
    }

    public void setQuestionAnswer(String questionAnswer) {
        this.questionAnswer = questionAnswer;
    }

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public Set<String> getCategories() {
        return categories;
    }

    public void setCategories(Set<String> categories) {
        this.categories = categories;
    }

    @Override
    public String toString() {
        return "Faq{" + "id=" + getId() + ", title='" + getQuestionTitle() + "'" + ", description='" + getQuestionTitle() + "'" + "}";
    }

}
