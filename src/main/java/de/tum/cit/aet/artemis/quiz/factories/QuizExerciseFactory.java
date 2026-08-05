package de.tum.cit.aet.artemis.quiz.factories;

import java.util.List;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.factories.ExerciseDates;
import de.tum.cit.aet.artemis.exercise.factories.ExerciseFactory;
import de.tum.cit.aet.artemis.quiz.domain.AnswerOption;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.domain.ScoringType;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerMapping;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSolution;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSpot;

/**
 * Factory for constructing {@link QuizExercise} objects and their questions that are not backed by user input, i.e. integration test fixtures and the demo course seeded by the
 * {@code demo} profile.
 * <p>
 * This factory only <b>constructs</b> the entities, it never persists them. Drag and drop questions are deliberately not covered: their background image has to go through the
 * multipart upload handling of {@code QuizExerciseService#handleDndQuizFileCreation}, so they cannot be built from an entity alone.
 */
public final class QuizExerciseFactory {

    private QuizExerciseFactory() {
        // static factory, do not instantiate
    }

    /**
     * Generates a quiz exercise for the given course. The caller is responsible for adding questions and for setting the max points afterwards, because the achievable points of a
     * quiz are the sum of its question points.
     *
     * @param title            The title of the exercise.
     * @param shortName        The short name of the exercise.
     * @param problemStatement The markdown problem statement shown to students.
     * @param dates            The release, start, due and assessment due dates.
     * @param quizMode         How students may participate: all at once, in batches, or individually.
     * @param duration         The working time in seconds.
     * @param course           The course the exercise belongs to.
     * @return The generated quiz exercise, without questions.
     */
    public static QuizExercise generateQuizExercise(String title, @Nullable String shortName, @Nullable String problemStatement, ExerciseDates dates, QuizMode quizMode,
            int duration, Course course) {
        // The max points of a quiz follow from its questions, so they are only known once the caller has added them.
        QuizExercise quizExercise = ExerciseFactory.populateExercise(new QuizExercise(), title, shortName, problemStatement, 1.0, 0.0, dates, course);
        quizExercise.setQuizMode(quizMode);
        quizExercise.setDuration(duration);
        return quizExercise;
    }

    /**
     * Generates a multiple choice question.
     *
     * @param title         The title of the question.
     * @param text          The question itself.
     * @param explanation   The explanation shown after the quiz has ended.
     * @param points        The achievable points. Must be greater than zero.
     * @param scoringType   How partially correct answers are scored. Single choice questions must use {@link ScoringType#ALL_OR_NOTHING}.
     * @param singleChoice  Whether exactly one answer option is correct.
     * @param answerOptions The answer options, at least one of which has to be correct.
     * @return The generated question.
     */
    public static MultipleChoiceQuestion generateMultipleChoiceQuestion(String title, String text, @Nullable String explanation, double points, ScoringType scoringType,
            boolean singleChoice, List<AnswerOption> answerOptions) {
        MultipleChoiceQuestion question = (MultipleChoiceQuestion) new MultipleChoiceQuestion().title(title).text(text).score(points);
        question.setScoringType(scoringType);
        question.setSingleChoice(singleChoice);
        question.setExplanation(explanation);
        question.setRandomizeOrder(true);
        answerOptions.forEach(question::addAnswerOption);
        return question;
    }

    /**
     * Generates an answer option of a multiple choice question.
     *
     * @param text        The answer itself.
     * @param isCorrect   Whether selecting this answer is correct.
     * @param explanation The explanation shown after the quiz has ended.
     * @return The generated answer option.
     */
    public static AnswerOption generateAnswerOption(String text, boolean isCorrect, @Nullable String explanation) {
        return new AnswerOption().text(text).isCorrect(isCorrect).explanation(explanation);
    }

    /**
     * Generates a short answer question with one spot per expected solution.
     * <p>
     * The spots are numbered starting at one and have to be referenced from the question text as {@code [-spot 1]}, {@code [-spot 2]} and so on, in the same order as the given
     * solutions.
     *
     * @param title                The title of the question.
     * @param text                 The question text, containing one {@code [-spot n]} placeholder per solution.
     * @param explanation          The explanation shown after the quiz has ended.
     * @param points               The achievable points. Must be greater than zero.
     * @param similarityValue      How closely an answer has to match the solution, between 50 and 100.
     * @param matchLetterCase      Whether the comparison is case sensitive.
     * @param solutionsInSpotOrder The expected solutions, in the order of the spots in the text.
     * @return The generated question.
     */
    public static ShortAnswerQuestion generateShortAnswerQuestion(String title, String text, @Nullable String explanation, double points, int similarityValue,
            boolean matchLetterCase, List<String> solutionsInSpotOrder) {
        ShortAnswerQuestion question = (ShortAnswerQuestion) new ShortAnswerQuestion().title(title).text(text).score(points);
        question.setScoringType(ScoringType.PROPORTIONAL_WITHOUT_PENALTY);
        question.setSimilarityValue(similarityValue);
        question.setMatchLetterCase(matchLetterCase);
        question.setExplanation(explanation);
        question.setRandomizeOrder(false);

        for (int index = 0; index < solutionsInSpotOrder.size(); index++) {
            // Spot numbers are one based because that is how they are referenced from the question text.
            ShortAnswerSpot spot = new ShortAnswerSpot().spotNr(index + 1).width(15);
            ShortAnswerSolution solution = new ShortAnswerSolution().text(solutionsInSpotOrder.get(index));
            question.addSpot(spot);
            question.addSolution(solution);
            question.addCorrectMapping(new ShortAnswerMapping().spot(spot).solution(solution));
        }
        return question;
    }
}
