package de.tum.cit.aet.artemis.quiz.dto.exercise;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.domain.GradingInstruction;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.domain.competency.Prerequisite;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.PointCounter;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizPointStatistic;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizStatistic;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;
import de.tum.cit.aet.artemis.quiz.dto.QuizQuestionStatisticDTO;
import de.tum.cit.aet.artemis.quiz.dto.QuizStatisticCounterDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.DragAndDropQuestionWithSolutionDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.MultipleChoiceQuestionWithSolutionDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.QuizQuestionBaseDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.QuizQuestionWithSolutionDTO;
import de.tum.cit.aet.artemis.quiz.dto.question.ShortAnswerQuestionWithMappingDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record QuizExerciseWithStatisticsDTO(@JsonUnwrapped QuizExerciseWithoutQuestionsDTO quizExercise, List<QuizQuestionWithStatisticsDTO> quizQuestions, Set<String> categories,
        QuizPointStatisticDTO quizPointStatistic, Set<CompetencyExerciseLinkDTO> competencyLinks, Set<GradingCriterionDTO> gradingCriteria, String channelName,
        Boolean testRunParticipationsExist, Boolean isEditable) {

    /**
     * Converts a QuizExercise entity to a QuizExerciseWithStatisticsDTO
     *
     * @param quizExercise the QuizExercise entity
     * @return the corresponding QuizExerciseWithStatisticsDTO
     */
    public static QuizExerciseWithStatisticsDTO of(QuizExercise quizExercise) {
        List<QuizQuestionWithStatisticsDTO> questionDTOs = quizExercise.getQuizQuestions().stream().map(QuizQuestionWithStatisticsDTO::of).toList();
        Set<String> categories = quizExercise.getCategories();
        QuizPointStatisticDTO quizPointStatisticDTO = null;
        if (quizExercise.getQuizPointStatistic() != null) {
            quizPointStatisticDTO = QuizPointStatisticDTO.of(quizExercise.getQuizPointStatistic());
        }
        Set<CompetencyExerciseLinkDTO> competencyExerciseLinkDTOs = null;
        if (Hibernate.isInitialized(quizExercise.getCompetencyLinks())) {
            competencyExerciseLinkDTOs = quizExercise.getCompetencyLinks().stream().map(CompetencyExerciseLinkDTO::of).collect(Collectors.toSet());
        }
        Set<GradingCriterionDTO> gradingCriterionDTOs = null;
        if (Hibernate.isInitialized(quizExercise.getGradingCriteria())) {
            gradingCriterionDTOs = quizExercise.getGradingCriteria().stream().map(GradingCriterionDTO::of).collect(Collectors.toSet());
        }

        return new QuizExerciseWithStatisticsDTO(QuizExerciseWithoutQuestionsDTO.of(quizExercise), questionDTOs, categories, quizPointStatisticDTO, competencyExerciseLinkDTOs,
                gradingCriterionDTOs, quizExercise.getChannelName(), quizExercise.getTestRunParticipationsExist(), null);
    }

    public static QuizExerciseWithStatisticsDTO of(QuizExercise quizExercise, Boolean isEditable) {
        QuizExerciseWithStatisticsDTO dto = of(quizExercise);
        return new QuizExerciseWithStatisticsDTO(dto.quizExercise, dto.quizQuestions, dto.categories, dto.quizPointStatistic, dto.competencyLinks, dto.gradingCriteria,
                dto.channelName, dto.testRunParticipationsExist, isEditable);
    }

    public static QuizExerciseWithStatisticsDTO of(QuizExercise quizExercise, Boolean isEditable, boolean effectiveQuizEnded) {
        QuizExerciseWithStatisticsDTO dto = of(quizExercise);
        return new QuizExerciseWithStatisticsDTO(QuizExerciseWithoutQuestionsDTO.of(quizExercise, effectiveQuizEnded), dto.quizQuestions, dto.categories, dto.quizPointStatistic,
                dto.competencyLinks, dto.gradingCriteria, dto.channelName, dto.testRunParticipationsExist, isEditable);
    }
}

/**
 * A quiz question with its statistic, flattened into one object.
 * <p>
 * One implementation per question type. {@code @JsonUnwrapped} cannot flatten a value that carries type information,
 * and {@code @JsonTypeInfo} is inherited by implementations, so naming the concrete {@link QuizQuestionWithSolutionDTO}
 * is not enough — each record holds the question's parts directly instead. The payload is unchanged: the shared fields
 * including {@code type}, the explanation, the question-type fields, and the statistic.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = true)
// @formatter:off
@JsonSubTypes({
    @JsonSubTypes.Type(value = MultipleChoiceQuizQuestionWithStatisticsDTO.class, name = "multiple-choice"),
    @JsonSubTypes.Type(value = DragAndDropQuizQuestionWithStatisticsDTO.class, name = "drag-and-drop"),
    @JsonSubTypes.Type(value = ShortAnswerQuizQuestionWithStatisticsDTO.class, name = "short-answer")
})
// @formatter:on
sealed interface QuizQuestionWithStatisticsDTO
        permits MultipleChoiceQuizQuestionWithStatisticsDTO, DragAndDropQuizQuestionWithStatisticsDTO, ShortAnswerQuizQuestionWithStatisticsDTO {

    /**
     * @return the statistic of this question, absent when it has none
     */
    QuizQuestionStatisticDTO quizQuestionStatistic();

    /**
     * Creates the projection matching the concrete question type.
     *
     * @param quizQuestion the question to project
     * @return the question with its statistic
     */
    static QuizQuestionWithStatisticsDTO of(QuizQuestion quizQuestion) {
        QuizQuestionStatisticDTO statistic = quizQuestion.getQuizQuestionStatistic() == null ? null : QuizQuestionStatisticDTO.of(quizQuestion.getQuizQuestionStatistic());
        QuizQuestionBaseDTO base = QuizQuestionBaseDTO.of(quizQuestion);
        String explanation = quizQuestion.getExplanation();
        return switch (quizQuestion) {
            case MultipleChoiceQuestion question ->
                new MultipleChoiceQuizQuestionWithStatisticsDTO(base, explanation, MultipleChoiceQuestionWithSolutionDTO.of(question), statistic);
            case DragAndDropQuestion question -> new DragAndDropQuizQuestionWithStatisticsDTO(base, explanation, DragAndDropQuestionWithSolutionDTO.of(question), statistic);
            case ShortAnswerQuestion question -> new ShortAnswerQuizQuestionWithStatisticsDTO(base, explanation, ShortAnswerQuestionWithMappingDTO.of(question), statistic);
            default -> throw new IllegalArgumentException("Unsupported quiz question type: " + quizQuestion.getClass().getSimpleName());
        };
    }
}

/**
 * A multiple-choice question with its statistic.
 *
 * @param quizQuestionBaseDTO                   the fields shared by every question type
 * @param explanation                           the explanation shown once solutions are published
 * @param multipleChoiceQuestionWithSolutionDTO the question-type specific fields, including the solution
 * @param quizQuestionStatistic                 the statistic of the question
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
record MultipleChoiceQuizQuestionWithStatisticsDTO(@JsonUnwrapped QuizQuestionBaseDTO quizQuestionBaseDTO, String explanation,
        @JsonUnwrapped MultipleChoiceQuestionWithSolutionDTO multipleChoiceQuestionWithSolutionDTO, QuizQuestionStatisticDTO quizQuestionStatistic)
        implements QuizQuestionWithStatisticsDTO {
}

/**
 * A drag-and-drop question with its statistic.
 *
 * @param quizQuestionBaseDTO                the fields shared by every question type
 * @param explanation                        the explanation shown once solutions are published
 * @param dragAndDropQuestionWithSolutionDTO the question-type specific fields, including the solution
 * @param quizQuestionStatistic              the statistic of the question
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
record DragAndDropQuizQuestionWithStatisticsDTO(@JsonUnwrapped QuizQuestionBaseDTO quizQuestionBaseDTO, String explanation,
        @JsonUnwrapped DragAndDropQuestionWithSolutionDTO dragAndDropQuestionWithSolutionDTO, QuizQuestionStatisticDTO quizQuestionStatistic)
        implements QuizQuestionWithStatisticsDTO {
}

/**
 * A short-answer question with its statistic.
 *
 * @param quizQuestionBaseDTO               the fields shared by every question type
 * @param explanation                       the explanation shown once solutions are published
 * @param shortAnswerQuestionWithMappingDTO the question-type specific fields, including the solution
 * @param quizQuestionStatistic             the statistic of the question
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
record ShortAnswerQuizQuestionWithStatisticsDTO(@JsonUnwrapped QuizQuestionBaseDTO quizQuestionBaseDTO, String explanation,
        @JsonUnwrapped ShortAnswerQuestionWithMappingDTO shortAnswerQuestionWithMappingDTO, QuizQuestionStatisticDTO quizQuestionStatistic)
        implements QuizQuestionWithStatisticsDTO {
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record QuizPointStatisticDTO(Set<PointCounterDTO> pointCounters, @JsonUnwrapped QuizStatisticDTO quizStatistic) {

    public static QuizPointStatisticDTO of(QuizPointStatistic quizPointStatistic) {
        Set<PointCounterDTO> pointCounterDTOs = quizPointStatistic.getPointCounters().stream().map(PointCounterDTO::of).collect(java.util.stream.Collectors.toSet());
        QuizStatisticDTO quizStatisticDTO = QuizStatisticDTO.of(quizPointStatistic);
        return new QuizPointStatisticDTO(pointCounterDTOs, quizStatisticDTO);
    }

}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record QuizStatisticDTO(Long id, Integer participantsRated, Integer participantsUnrated) {

    public static QuizStatisticDTO of(QuizStatistic quizStatistic) {
        return new QuizStatisticDTO(quizStatistic.getId(), quizStatistic.getParticipantsRated(), quizStatistic.getParticipantsUnrated());
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record PointCounterDTO(Double points, @JsonUnwrapped QuizStatisticCounterDTO quizStatisticCounter) {

    public static PointCounterDTO of(PointCounter pointCounter) {
        return new PointCounterDTO(pointCounter.getPoints(), QuizStatisticCounterDTO.of(pointCounter));
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record CompetencyExerciseLinkDTO(double weight, CourseCompetencyDTO competency) {

    public static CompetencyExerciseLinkDTO of(CompetencyExerciseLink competencyExerciseLink) {
        return new CompetencyExerciseLinkDTO(competencyExerciseLink.getWeight(), CourseCompetencyDTO.of(competencyExerciseLink.getCompetency()));
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record CourseCompetencyDTO(@JsonUnwrapped BaseCompetencyDTO baseCompetency, ZonedDateTime softDueDate, int masteryThreshold, boolean optional, String type) {

    public static CourseCompetencyDTO of(CourseCompetency courseCompetency) {
        String type = null;
        if (courseCompetency instanceof Competency) {
            type = "competency";
        }
        else if (courseCompetency instanceof Prerequisite) {
            type = "prerequisite";
        }
        return new CourseCompetencyDTO(BaseCompetencyDTO.of(courseCompetency), courseCompetency.getSoftDueDate(), courseCompetency.getMasteryThreshold(),
                courseCompetency.isOptional(), type);
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record BaseCompetencyDTO(Long id, String title, String description, CompetencyTaxonomy taxonomy) {

    public static BaseCompetencyDTO of(CourseCompetency courseCompetency) {
        return new BaseCompetencyDTO(courseCompetency.getId(), courseCompetency.getTitle(), courseCompetency.getDescription(), courseCompetency.getTaxonomy());
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record GradingCriterionDTO(Long id, String title, Set<GradingInstructionDTO> structuredGradingInstructions) {

    public static GradingCriterionDTO of(GradingCriterion gradingCriterion) {
        Set<GradingInstructionDTO> gradingInstructionDTOs = Set.of();
        if (Hibernate.isInitialized(gradingCriterion.getStructuredGradingInstructions())) {
            gradingInstructionDTOs = gradingCriterion.getStructuredGradingInstructions().stream().map(GradingInstructionDTO::of).collect(Collectors.toSet());
        }
        return new GradingCriterionDTO(gradingCriterion.getId(), gradingCriterion.getTitle(), gradingInstructionDTOs);
    }
}

@JsonInclude(JsonInclude.Include.NON_EMPTY)
record GradingInstructionDTO(Long id, double credits, String gradingScale, String instructionDescription, String feedback, int usageCount) {

    public static GradingInstructionDTO of(GradingInstruction gradingInstruction) {
        return new GradingInstructionDTO(gradingInstruction.getId(), gradingInstruction.getCredits(), gradingInstruction.getGradingScale(),
                gradingInstruction.getInstructionDescription(), gradingInstruction.getFeedback(), gradingInstruction.getUsageCount());
    }
}
