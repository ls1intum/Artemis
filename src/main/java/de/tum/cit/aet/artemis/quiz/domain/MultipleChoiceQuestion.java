package de.tum.cit.aet.artemis.quiz.domain;

import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.quiz.domain.scoring.ScoringStrategy;
import de.tum.cit.aet.artemis.quiz.domain.scoring.ScoringStrategyMultipleChoiceAllOrNothing;
import de.tum.cit.aet.artemis.quiz.domain.scoring.ScoringStrategyMultipleChoiceProportionalWithPenalty;
import de.tum.cit.aet.artemis.quiz.domain.scoring.ScoringStrategyMultipleChoiceProportionalWithoutPenalty;

/**
 * A MultipleChoiceQuestion.
 * <p>
 * Its answer options are no longer a separate JPA entity table: they are stored inside the {@code quiz_question.content} JSON column as a {@link MultipleChoiceQuestionContent}.
 * This eliminates the eager {@code @OneToMany} fan-out. The public {@code getAnswerOptions()} accessor keeps its original signature and delegates to the content, so the
 * REST/websocket wire format and all callers are preserved. Mirrors {@link DragAndDropQuestion}.
 */
@Entity
@DiscriminatorValue(value = "MC")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class MultipleChoiceQuestion extends QuizQuestion {

    @Column(name = "single_choice")
    private boolean singleChoice = false;

    /**
     * @return the multiple-choice content, creating and attaching an empty one if none exists yet.
     */
    private MultipleChoiceQuestionContent mcContent() {
        if (getContent() instanceof MultipleChoiceQuestionContent multipleChoiceContent) {
            return multipleChoiceContent;
        }
        MultipleChoiceQuestionContent created = new MultipleChoiceQuestionContent();
        setContent(created);
        return created;
    }

    /**
     * Mint a fresh, question-scoped component id: one greater than the largest id currently used by any answer option of this question.
     *
     * @return the next free component id
     */
    private long nextComponentId() {
        long max = 0;
        for (Long id : mcContent().componentIds()) {
            if (id != null && id > max) {
                max = id;
            }
        }
        return max + 1;
    }

    /**
     * Assign a fresh, question-scoped id to every component in the given list that does not have one yet. Called from the entity-level bulk setter used by the create/edit/import
     * flows; the JSON deserialization path goes through {@link MultipleChoiceQuestionContent}'s own setter instead and therefore preserves existing ids.
     *
     * @param components the components to assign ids to
     */
    private void assignMissingComponentIds(List<? extends DomainObject> components) {
        for (var component : components) {
            if (component.getId() == null) {
                component.setId(nextComponentId());
            }
        }
    }

    /**
     * Mint a fresh, question-scoped id for any answer option added without one (e.g. via {@code getAnswerOptions().add(...)}, which bypasses {@link #addAnswerOption}). Called
     * before persisting so the statistics counters (keyed by answer-option id) and the stored JSON content stay id-consistent.
     */
    public void assignMissingComponentIds() {
        assignMissingComponentIds(getAnswerOptions());
    }

    public List<AnswerOption> getAnswerOptions() {
        return mcContent().getAnswerOptions();
    }

    public void setAnswerOptions(List<AnswerOption> answerOptions) {
        mcContent().setAnswerOptions(answerOptions);
        assignMissingComponentIds(mcContent().getAnswerOptions());
    }

    public boolean isSingleChoice() {
        return singleChoice;
    }

    public void setSingleChoice(boolean singleChoice) {
        this.singleChoice = singleChoice;
    }

    /**
     * Get answerOption by ID
     *
     * @param answerOptionId the ID of the answerOption, which should be found
     * @return the answerOption with the given ID, or null if the answerOption is not contained in this question
     */
    public AnswerOption findAnswerOptionById(Long answerOptionId) {
        if (answerOptionId != null) {
            for (AnswerOption answer : mcContent().getAnswerOptions()) {
                if (answerOptionId.equals(answer.getId())) {
                    return answer;
                }
            }
        }
        return null;
    }

    @Override
    @JsonIgnore
    public void initializeStatistic() {
        setQuizQuestionStatistic(new MultipleChoiceQuestionStatistic());
    }

    @Override
    public void filterForStudentsDuringQuiz() {
        super.filterForStudentsDuringQuiz();
        for (AnswerOption answerOption : getAnswerOptions()) {
            answerOption.setIsCorrect(null);
            answerOption.setExplanation(null);
        }
    }

    @Override
    public void filterForStatisticWebsocket() {
        super.filterForStatisticWebsocket();
        for (AnswerOption answerOption : getAnswerOptions()) {
            answerOption.setIsCorrect(null);
            answerOption.setExplanation(null);
        }
    }

    @Override
    public Boolean isValid() {
        // check general validity (using superclass)
        if (!super.isValid()) {
            return false;
        }

        // if there is only a single correct answer only ALL_OR_NOTHING scoring makes sense
        if (isSingleChoice() && getScoringType() != ScoringType.ALL_OR_NOTHING) {
            return false;
        }

        int correctAnswerCount = 0;

        // check answer options
        for (AnswerOption answerOption : getAnswerOptions()) {
            if (Boolean.TRUE.equals(answerOption.isIsCorrect())) {
                correctAnswerCount++;
            }
        }

        return isSingleChoice() ? correctAnswerCount == 1 : correctAnswerCount > 0;
    }

    /**
     * creates an instance of ScoringStrategy with the appropriate type for the given multiple choice question (based on polymorphism)
     *
     * @return an instance of the appropriate implementation of ScoringStrategy
     */
    @Override
    public ScoringStrategy makeScoringStrategy() {
        return switch (getScoringType()) {
            case ALL_OR_NOTHING -> new ScoringStrategyMultipleChoiceAllOrNothing();
            case PROPORTIONAL_WITH_PENALTY -> new ScoringStrategyMultipleChoiceProportionalWithPenalty();
            case PROPORTIONAL_WITHOUT_PENALTY -> new ScoringStrategyMultipleChoiceProportionalWithoutPenalty();
        };
    }

    @Override
    public String toString() {
        return "MultipleChoiceQuestion{" + "id=" + getId() + ", title='" + getTitle() + "'" + ", text='" + getText() + "'" + ", hint='" + getHint() + "'" + ", explanation='"
                + getExplanation() + "'" + ", score='" + getPoints() + "'" + ", scoringType='" + getScoringType() + "'" + ", randomizeOrder='" + isRandomizeOrder() + "'"
                + ", exerciseTitle='" + ((getExercise() == null) ? null : getExercise().getTitle()) + "'" + "}";
    }

    @Override
    public QuizQuestion copyQuestionId() {
        var question = new MultipleChoiceQuestion();
        question.setId(getId());
        return question;
    }

    /**
     * Adds a single answer option, assigning it a fresh question-scoped id if it does not have one yet.
     *
     * @param answerOption the answer option to add
     * @return this question for fluent chaining
     */
    public MultipleChoiceQuestion addAnswerOption(AnswerOption answerOption) {
        if (answerOption.getId() == null) {
            answerOption.setId(nextComponentId());
        }
        mcContent().getAnswerOptions().add(answerOption);
        return this;
    }

    /**
     * Removes a single answer option.
     *
     * @param answerOption the answer option to remove
     */
    public void removeAnswerOption(AnswerOption answerOption) {
        mcContent().getAnswerOptions().remove(answerOption);
    }
}
