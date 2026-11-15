package de.tum.cit.aet.artemis.communication.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.AbstractAuditingEntity;
import de.tum.cit.aet.artemis.core.domain.Parent;
import de.tum.cit.aet.artemis.course.domain.Course;

/**
 * A FAQ.
 */
@Entity
@Table(name = "faq")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Faq extends AbstractAuditingEntity {

    @Column(name = "question_title")
    private String questionTitle;

    @Column(name = "question_answer")
    private String questionAnswer;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "faq_category", joinColumns = @JoinColumn(name = "faq_id"))
    @Column(name = "category")
    private Set<String> categories = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "faq_state")
    private FaqState faqState;

    @ManyToOne
    @JsonIgnoreProperties(value = { "faqs" }, allowSetters = true)
    @JoinColumn(nullable = false)
    @Parent
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

    public FaqState getFaqState() {
        return faqState;
    }

    public void setFaqState(FaqState faqState) {
        this.faqState = faqState;
    }

    @Override
    public String toString() {
        return "Faq{" + "id=" + getId() + ", questionTitle='" + getQuestionTitle() + "'" + ", questionAnswer='" + getQuestionAnswer() + "'" + ", faqState='" + getFaqState() + "}";
    }

    /**
     * The fingerprint of this FAQ's content as Iris stores it, see {@link #contentFingerprint(String, String)}.
     *
     * @return the versioned fingerprint of the question and the answer
     */
    public String computeContentFingerprint() {
        return contentFingerprint(questionTitle, questionAnswer);
    }

    /**
     * The fingerprint of an FAQ's content: the question and the answer, the two inputs of its Iris embedding. Each part is hashed on its own
     * before the parts are combined, so no title and answer pair can collide with another by moving text from one to the other. A missing part
     * counts as empty. Artemis sends it with every FAQ it pushes to Iris and compares it with what the FAQ census reports.
     *
     * @param questionTitle  the question, may be null
     * @param questionAnswer the answer, may be null
     * @return the versioned fingerprint
     */
    public static String contentFingerprint(@Nullable String questionTitle, @Nullable String questionAnswer) {
        return "v1:" + sha256Hex(sha256Hex(questionTitle == null ? "" : questionTitle) + ":" + sha256Hex(questionAnswer == null ? "" : questionAnswer));
    }

    private static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
