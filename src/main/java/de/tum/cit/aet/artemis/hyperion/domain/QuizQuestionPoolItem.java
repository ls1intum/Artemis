package de.tum.cit.aet.artemis.hyperion.domain;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.Parent;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.hyperion.dto.GeneratedQuizQuestionDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionFilterDecisionDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationLanguage;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationType;

/**
 * One intended question of a course's quiz question pool.
 * <p>
 * The pool is a grid of cells, one per combination of competency, lecture unit linked to that competency, language, question type
 * and difficulty, and each cell holds a fixed number of items numbered by {@code itemIndex}. An item is created in state {@link QuizQuestionPoolItemState#PENDING} and
 * carries its generated question once generated and the filter's decision once judged.
 */
@Entity
@Table(name = "quiz_question_pool_item")
public class QuizQuestionPoolItem extends DomainObject {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    @Parent
    private Course course;

    @Column(name = "competency_id", nullable = false)
    private Long competencyId;

    @Column(name = "lecture_unit_id", nullable = false)
    private Long lectureUnitId;

    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false)
    private QuizQuestionGenerationLanguage language;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false)
    private QuizQuestionGenerationType questionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty", nullable = false)
    private QuizQuestionDifficulty difficulty;

    @Column(name = "item_index", nullable = false)
    private int itemIndex;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private QuizQuestionPoolItemState state = QuizQuestionPoolItemState.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_state", nullable = false)
    private QuizQuestionReviewState reviewState = QuizQuestionReviewState.PENDING;

    @Column(name = "generation_attempts", nullable = false)
    private int generationAttempts;

    @Column(name = "filter_attempts", nullable = false)
    private int filterAttempts;

    /** Why the last generation or filter attempt produced nothing usable; {@code null} while no attempt failed. */
    @Column(name = "failure")
    @Nullable
    private String failure;

    @Column(name = "generator_model")
    @Nullable
    private String generatorModel;

    @Column(name = "judge_model")
    @Nullable
    private String judgeModel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "question", columnDefinition = "json")
    @Nullable
    private GeneratedQuizQuestionDTO question;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "decision", columnDefinition = "json")
    @Nullable
    private QuizQuestionFilterDecisionDTO decision;

    /** The decision's outcome, duplicated from {@link #decision} so it can be counted in queries; {@code null} until judged. */
    @Column(name = "accepted")
    @Nullable
    private Boolean accepted;

    @Column(name = "claimed_at")
    @Nullable
    private ZonedDateTime claimedAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt = ZonedDateTime.now();

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public Long getCompetencyId() {
        return competencyId;
    }

    public void setCompetencyId(Long competencyId) {
        this.competencyId = competencyId;
    }

    public Long getLectureUnitId() {
        return lectureUnitId;
    }

    public void setLectureUnitId(Long lectureUnitId) {
        this.lectureUnitId = lectureUnitId;
    }

    public QuizQuestionGenerationLanguage getLanguage() {
        return language;
    }

    public void setLanguage(QuizQuestionGenerationLanguage language) {
        this.language = language;
    }

    public QuizQuestionGenerationType getQuestionType() {
        return questionType;
    }

    public void setQuestionType(QuizQuestionGenerationType questionType) {
        this.questionType = questionType;
    }

    public QuizQuestionDifficulty getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(QuizQuestionDifficulty difficulty) {
        this.difficulty = difficulty;
    }

    public int getItemIndex() {
        return itemIndex;
    }

    public void setItemIndex(int itemIndex) {
        this.itemIndex = itemIndex;
    }

    public QuizQuestionPoolItemState getState() {
        return state;
    }

    public void setState(QuizQuestionPoolItemState state) {
        this.state = state;
    }

    public QuizQuestionReviewState getReviewState() {
        return reviewState;
    }

    public void setReviewState(QuizQuestionReviewState reviewState) {
        this.reviewState = reviewState;
    }

    public int getGenerationAttempts() {
        return generationAttempts;
    }

    public void setGenerationAttempts(int generationAttempts) {
        this.generationAttempts = generationAttempts;
    }

    public int getFilterAttempts() {
        return filterAttempts;
    }

    public void setFilterAttempts(int filterAttempts) {
        this.filterAttempts = filterAttempts;
    }

    @Nullable
    public String getFailure() {
        return failure;
    }

    public void setFailure(@Nullable String failure) {
        this.failure = failure;
    }

    @Nullable
    public String getGeneratorModel() {
        return generatorModel;
    }

    public void setGeneratorModel(@Nullable String generatorModel) {
        this.generatorModel = generatorModel;
    }

    @Nullable
    public String getJudgeModel() {
        return judgeModel;
    }

    public void setJudgeModel(@Nullable String judgeModel) {
        this.judgeModel = judgeModel;
    }

    @Nullable
    public GeneratedQuizQuestionDTO getQuestion() {
        return question;
    }

    public void setQuestion(@Nullable GeneratedQuizQuestionDTO question) {
        this.question = question;
    }

    @Nullable
    public QuizQuestionFilterDecisionDTO getDecision() {
        return decision;
    }

    public void setDecision(@Nullable QuizQuestionFilterDecisionDTO decision) {
        this.decision = decision;
    }

    @Nullable
    public Boolean getAccepted() {
        return accepted;
    }

    public void setAccepted(@Nullable Boolean accepted) {
        this.accepted = accepted;
    }

    @Nullable
    public ZonedDateTime getClaimedAt() {
        return claimedAt;
    }

    public void setClaimedAt(@Nullable ZonedDateTime claimedAt) {
        this.claimedAt = claimedAt;
    }

    public ZonedDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(ZonedDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
