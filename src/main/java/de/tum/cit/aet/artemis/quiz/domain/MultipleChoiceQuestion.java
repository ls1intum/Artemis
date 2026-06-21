package de.tum.cit.aet.artemis.quiz.domain;

import static de.tum.cit.aet.artemis.core.config.Constants.MAX_QUIZ_ANSWER_OPTION_EXPLANATION_LENGTH;
import static de.tum.cit.aet.artemis.core.config.Constants.MAX_QUIZ_ANSWER_OPTION_HINT_LENGTH;
import static de.tum.cit.aet.artemis.core.config.Constants.MAX_QUIZ_ANSWER_OPTION_TEXT_LENGTH;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.scoring.ScoringStrategy;
import de.tum.cit.aet.artemis.quiz.domain.scoring.ScoringStrategyMultipleChoiceAllOrNothing;
import de.tum.cit.aet.artemis.quiz.domain.scoring.ScoringStrategyMultipleChoiceProportionalWithPenalty;
import de.tum.cit.aet.artemis.quiz.domain.scoring.ScoringStrategyMultipleChoiceProportionalWithoutPenalty;

/**
 * A MultipleChoiceQuestion.
 */
@Entity
@DiscriminatorValue(value = "MC")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class MultipleChoiceQuestion extends QuizQuestion {

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "answer_options")
    private List<AnswerOption> answerOptions = new ArrayList<>();

    @Column(name = "single_choice")
    private boolean singleChoice = false;

    public MultipleChoiceQuestion() {
        setNextComponentId(1L);
    }

    public List<AnswerOption> getAnswerOptions() {
        return Collections.unmodifiableList(answerOptions);
    }

    /**
     * Replaces the JSON-owned answer options while preserving existing question-scoped IDs where possible.
     *
     * @param answerOptions the answer options to persist with this question
     */
    public void setAnswerOptions(List<AnswerOption> answerOptions) {
        this.answerOptions = copyAnswerOptions(answerOptions);
        advanceNextComponentIdPastExistingOptionIds();
        assignIdsToNewOptions();
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
        if (answerOptionId == null) {
            return null;
        }
        return answerOptions.stream().filter(answer -> Objects.equals(answer.getId(), answerOptionId)).findFirst().orElse(null);
    }

    /**
     * Replaces the complete ordered option list while preserving known IDs and allocating IDs for new options.
     *
     * @param inputs immutable option inputs in their requested order
     * @return semantic changes caused by the replacement
     */
    public AnswerOptionChangeSet replaceAnswerOptions(List<AnswerOptionInput> inputs) {
        Objects.requireNonNull(inputs);

        // Index the current JSON components by their stable question-scoped IDs for update/delete detection.
        Map<Long, AnswerOption> existingById = answerOptions.stream().collect(Collectors.toMap(AnswerOption::getId, Function.identity()));
        Set<Long> requestedIds = new HashSet<>();
        Set<Long> addedIds = new LinkedHashSet<>();
        Set<Long> updatedIds = new LinkedHashSet<>();
        boolean requiresRecalculation = false;
        List<AnswerOption> replacements = new ArrayList<>();

        for (AnswerOptionInput input : inputs) {
            validateInput(input);

            if (input.id() != null && !requestedIds.add(input.id())) {
                throw new IllegalArgumentException("Duplicate answer option ID " + input.id());
            }

            AnswerOption existing = input.id() == null ? null : existingById.get(input.id());
            if (input.id() != null && existing == null) {
                throw new IllegalArgumentException("Unknown answer option ID " + input.id());
            }

            // New inputs have no ID yet; allocate a fresh one and never reuse deleted IDs.
            if (existing == null) {
                AnswerOption newOption = createNewAnswerOption(input);
                replacements.add(newOption);
                addedIds.add(newOption.getId());
                continue;
            }

            // Existing inputs keep their IDs so submissions, counters, and editor payloads remain stable.
            AnswerOption replacement = createAnswerOption(existing.getId(), input);
            replacements.add(replacement);
            if (!sameContent(existing, replacement)) {
                updatedIds.add(replacement.getId());
                requiresRecalculation |= isScoringRelevantChange(existing, replacement);
            }
        }

        // Any existing ID that was not requested disappeared from the ordered option list.
        Set<Long> deletedIds = new LinkedHashSet<>(existingById.keySet());
        deletedIds.removeAll(requestedIds);
        requiresRecalculation |= !deletedIds.isEmpty();

        answerOptions = replacements;
        validateAnswerOptions();
        return new AnswerOptionChangeSet(Set.copyOf(addedIds), Set.copyOf(updatedIds), Set.copyOf(deletedIds), requiresRecalculation);
    }

    /**
     * check if an update of the Results and Statistics is necessary
     *
     * @param originalQuizQuestion the original QuizQuestion-object, which will be compared with this question
     * @return a boolean which is true if the answer-changes make an update necessary and false if not
     */
    @Override
    public boolean isUpdateOfResultsAndStatisticsNecessary(QuizQuestion originalQuizQuestion) {
        if (originalQuizQuestion instanceof MultipleChoiceQuestion mcOriginalQuestion) {
            return checkAnswersIfRecalculationIsNecessary(mcOriginalQuestion);
        }
        return false;
    }

    @Override
    @JsonIgnore
    public void initializeStatistic() {
        setQuizQuestionStatistic(new MultipleChoiceQuestionStatistic());
    }

    /**
     * check if an update of the Results and Statistics is necessary
     *
     * @param originalQuestion the original MultipleChoiceQuestion-object, which will be compared with this question
     * @return a boolean which is true if the answer-changes make an update necessary and false if not
     */
    private boolean checkAnswersIfRecalculationIsNecessary(MultipleChoiceQuestion originalQuestion) {

        boolean updateNecessary = false;

        // check every answer of the question
        for (AnswerOption answer : this.getAnswerOptions()) {
            // check if the answer were already in the originalQuizExercise
            if (originalQuestion.findAnswerOptionById(answer.getId()) != null) {
                // find original answer
                AnswerOption originalAnswer = originalQuestion.findAnswerOptionById(answer.getId());

                // check if an answer is set invalid or if the correctness has changed
                // if true an update of the Statistics and Results is necessary
                if ((answer.isInvalid() && !this.isInvalid() && originalAnswer.isInvalid() == null) || (answer.isInvalid() && !this.isInvalid() && !originalAnswer.isInvalid())
                        || (!(answer.isIsCorrect().equals(originalAnswer.isIsCorrect())))) {
                    updateNecessary = true;
                }
            }
        }
        // check if an answer was deleted (not allowed added answers are not relevant)
        // if true an update of the Statistics and Results is necessary
        if (this.getAnswerOptions().size() < originalQuestion.getAnswerOptions().size()) {
            updateNecessary = true;
        }
        return updateNecessary;
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
        if (getAnswerOptions() != null) {
            for (AnswerOption answerOption : getAnswerOptions()) {
                if (answerOption.isIsCorrect()) {
                    correctAnswerCount++;
                }
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
        question.setVersion(getVersion());
        return question;
    }

    /**
     * Adds a single answer option as a JSON-owned component and allocates a question-scoped ID.
     *
     * @param answerOption the answer option to add
     * @return the newly added answer option with its question-scoped ID
     */
    public AnswerOption addAnswerOption(AnswerOption answerOption) {
        AnswerOptionInput input = AnswerOptionInput.of(answerOption);
        validateInput(input);
        AnswerOption copy = new AnswerOption(allocateNextComponentId(), input.text(), input.hint(), input.explanation(), input.isCorrect(), input.invalid());
        answerOptions.add(copy);
        return copy;
    }

    /**
     * Removes a single answer option from the JSON-owned component list.
     *
     * @param answerOption the answer option to remove
     */
    public void removeAnswerOption(AnswerOption answerOption) {
        if (answerOptions != null) {
            answerOptions.removeIf(option -> Objects.equals(option.getId(), answerOption.getId()));
        }
    }

    /**
     * Validates JSON-owned answer option invariants before persisting or updating the question.
     */
    @PrePersist
    @PreUpdate
    public void validateAnswerOptions() {
        if (answerOptions == null) {
            throw new IllegalStateException("Answer options must not be null");
        }
        Set<Long> ids = new HashSet<>();
        for (AnswerOption answerOption : answerOptions) {
            validateInput(AnswerOptionInput.of(answerOption));
            if (answerOption.getId() == null || !ids.add(answerOption.getId())) {
                throw new IllegalStateException("Answer option IDs must be non-null and unique");
            }
            if (getNextComponentId() == null || answerOption.getId() >= getNextComponentId()) {
                throw new IllegalStateException("Answer option ID must be below nextComponentId");
            }
        }
    }

    private void assignIdsToNewOptions() {
        answerOptions.stream().filter(option -> option.getId() == null).forEach(option -> option.setId(allocateNextComponentId()));
    }

    private static List<AnswerOption> copyAnswerOptions(List<AnswerOption> answerOptions) {
        if (answerOptions == null) {
            return new ArrayList<>();
        }
        return answerOptions.stream().map(AnswerOptionInput::of).map(MultipleChoiceQuestion::createAnswerOptionPreservingId).collect(Collectors.toCollection(ArrayList::new));
    }

    private void advanceNextComponentIdPastExistingOptionIds() {
        long nextIdAfterExistingOptions = answerOptions.stream().map(AnswerOption::getId).filter(Objects::nonNull).mapToLong(Long::longValue).max().orElse(0L) + 1L;
        setNextComponentId(Math.max(getNextComponentId() == null ? 1L : getNextComponentId(), nextIdAfterExistingOptions));
    }

    private AnswerOption createNewAnswerOption(AnswerOptionInput input) {
        return createAnswerOption(allocateNextComponentId(), input);
    }

    private static AnswerOption createAnswerOptionPreservingId(AnswerOptionInput input) {
        return createAnswerOption(input.id(), input);
    }

    private static AnswerOption createAnswerOption(Long id, AnswerOptionInput input) {
        return new AnswerOption(id, input.text(), input.hint(), input.explanation(), input.isCorrect(), input.invalid());
    }

    private static boolean sameContent(AnswerOption left, AnswerOption right) {
        return Objects.equals(left.getText(), right.getText()) && Objects.equals(left.getHint(), right.getHint()) && Objects.equals(left.getExplanation(), right.getExplanation())
                && Objects.equals(left.isIsCorrect(), right.isIsCorrect()) && Objects.equals(left.isInvalid(), right.isInvalid());
    }

    private static boolean isScoringRelevantChange(AnswerOption existing, AnswerOption replacement) {
        return !Objects.equals(existing.isIsCorrect(), replacement.isIsCorrect()) || !Objects.equals(existing.isInvalid(), replacement.isInvalid());
    }

    private static void validateInput(AnswerOptionInput input) {
        if (input == null || input.text() == null || input.text().isBlank()) {
            throw new IllegalArgumentException("Answer option text must not be blank");
        }
        if (input.text().length() > MAX_QUIZ_ANSWER_OPTION_TEXT_LENGTH || input.hint() != null && input.hint().length() > MAX_QUIZ_ANSWER_OPTION_HINT_LENGTH
                || input.explanation() != null && input.explanation().length() > MAX_QUIZ_ANSWER_OPTION_EXPLANATION_LENGTH) {
            throw new IllegalArgumentException("Answer option text, hint, or explanation exceeds its maximum length");
        }
        if (input.isCorrect() == null || input.invalid() == null) {
            throw new IllegalArgumentException("Answer option boolean fields must not be null");
        }
    }
}
