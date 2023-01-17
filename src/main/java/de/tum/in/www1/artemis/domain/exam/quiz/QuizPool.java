package de.tum.in.www1.artemis.domain.exam.quiz;

import java.time.ZonedDateTime;
import java.util.*;

import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.enumeration.QuizMode;
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

    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "quiz_pool_id", referencedColumnName = "id")
    private List<QuizQuestion> quizQuestions;

    public QuizPool() {
        this.quizQuestions = new ArrayList<>();
    }

    public void setExam(Exam exam) {
        this.exam = exam;
    }

    public Exam getExam() {
        return exam;
    }

    @Override
    public void setMaxPoints(Double maxPoints) {
    }

    @Override
    public Double getOverallQuizPoints() {
        return null;
    }

    @Override
    public QuizPointStatistic getQuizPointStatistic() {
        return null;
    }

    @Override
    public void setQuizPointStatistic(QuizPointStatistic quizPointStatistic) {
    }

    @Override
    public void recalculatePointCounters() {
    }

    public List<QuizQuestion> getQuizQuestions() {
        return this.quizQuestions;
    }

    @Override
    public Set<QuizBatch> getQuizBatches() {
        return null;
    }

    @Override
    public QuizMode getQuizMode() {
        return null;
    }

    @Override
    public void setDueDate(ZonedDateTime dueDate) {
    }

    @Override
    public Integer getDuration() {
        return null;
    }

    @Override
    public boolean isCourseExercise() {
        return false;
    }

    @Override
    public void setQuestionParent(QuizQuestion quizQuestion) {
        quizQuestion.setQuizPool(this);
    }

    @Override
    public void setQuizBatchParent(QuizBatch quizBatch) {
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

    public void reconnectJSONIgnoreAttributes() {
        for (QuizQuestion quizQuestion : getQuizQuestions()) {
            if (quizQuestion.getId() != null) {
                quizQuestion.setQuizPool(this);
                // reconnect QuestionStatistics
                if (quizQuestion.getQuizQuestionStatistic() != null) {
                    quizQuestion.getQuizQuestionStatistic().setQuizQuestion(quizQuestion);
                }
                // do the same for answerOptions (if quizQuestion is multiple choice)
                if (quizQuestion instanceof MultipleChoiceQuestion mcQuestion) {
                    MultipleChoiceQuestionStatistic mcStatistic = (MultipleChoiceQuestionStatistic) mcQuestion.getQuizQuestionStatistic();
                    // reconnect answerCounters
                    for (AnswerCounter answerCounter : mcStatistic.getAnswerCounters()) {
                        if (answerCounter.getId() != null) {
                            answerCounter.setMultipleChoiceQuestionStatistic(mcStatistic);
                        }
                    }
                    // reconnect answerOptions
                    for (AnswerOption answerOption : mcQuestion.getAnswerOptions()) {
                        if (answerOption.getId() != null) {
                            answerOption.setQuestion(mcQuestion);
                        }
                    }
                }
                if (quizQuestion instanceof DragAndDropQuestion dragAndDropQuestion) {
                    DragAndDropQuestionStatistic dragAndDropStatistic = (DragAndDropQuestionStatistic) dragAndDropQuestion.getQuizQuestionStatistic();
                    // reconnect dropLocations
                    for (DropLocation dropLocation : dragAndDropQuestion.getDropLocations()) {
                        if (dropLocation.getId() != null) {
                            dropLocation.setQuestion(dragAndDropQuestion);
                        }
                    }
                    // reconnect dragItems
                    for (DragItem dragItem : dragAndDropQuestion.getDragItems()) {
                        if (dragItem.getId() != null) {
                            dragItem.setQuestion(dragAndDropQuestion);
                        }
                    }
                    // reconnect correctMappings
                    for (DragAndDropMapping mapping : dragAndDropQuestion.getCorrectMappings()) {
                        if (mapping.getId() != null) {
                            mapping.setQuestion(dragAndDropQuestion);
                        }
                    }
                    // reconnect dropLocationCounters
                    for (DropLocationCounter dropLocationCounter : dragAndDropStatistic.getDropLocationCounters()) {
                        if (dropLocationCounter.getId() != null) {
                            dropLocationCounter.setDragAndDropQuestionStatistic(dragAndDropStatistic);
                            dropLocationCounter.getDropLocation().setQuestion(dragAndDropQuestion);
                        }
                    }
                }
                if (quizQuestion instanceof ShortAnswerQuestion shortAnswerQuestion) {
                    ShortAnswerQuestionStatistic shortAnswerStatistic = (ShortAnswerQuestionStatistic) shortAnswerQuestion.getQuizQuestionStatistic();
                    // reconnect spots
                    for (ShortAnswerSpot spot : shortAnswerQuestion.getSpots()) {
                        if (spot.getId() != null) {
                            spot.setQuestion(shortAnswerQuestion);
                        }
                    }
                    // reconnect solutions
                    for (ShortAnswerSolution solution : shortAnswerQuestion.getSolutions()) {
                        if (solution.getId() != null) {
                            solution.setQuestion(shortAnswerQuestion);
                        }
                    }
                    // reconnect correctMappings
                    for (ShortAnswerMapping mapping : shortAnswerQuestion.getCorrectMappings()) {
                        if (mapping.getId() != null) {
                            mapping.setQuestion(shortAnswerQuestion);
                        }
                    }
                    // reconnect spotCounters
                    for (ShortAnswerSpotCounter shortAnswerSpotCounter : shortAnswerStatistic.getShortAnswerSpotCounters()) {
                        if (shortAnswerSpotCounter.getId() != null) {
                            shortAnswerSpotCounter.setShortAnswerQuestionStatistic(shortAnswerStatistic);
                            shortAnswerSpotCounter.getSpot().setQuestion(shortAnswerQuestion);
                        }
                    }
                }
            }
        }
    }
}
