package de.tum.cit.aet.artemis.quiz.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.api.AbstractApi;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.factories.ExerciseDates;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.domain.ScoringType;
import de.tum.cit.aet.artemis.quiz.factories.QuizExerciseFactory;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.quiz.service.QuizExerciseService;

/**
 * API for quiz exercise functionality that other modules need to access.
 */
@Profile(PROFILE_CORE)
@Controller
@Lazy
public class QuizExerciseApi implements AbstractApi {

    /**
     * Title of the demo quiz exercise. Used as the idempotency key of {@link #createDemo(Course)} together with the course, so it must stay stable.
     */
    private static final String DEMO_EXERCISE_TITLE = "Quiz: Java Collections and Complexity";

    private static final String DEMO_EXERCISE_SHORT_NAME = "demoquiz";

    /**
     * Working time of the demo quiz in seconds.
     */
    private static final int DEMO_DURATION_SECONDS = 600;

    private static final String DEMO_PROBLEM_STATEMENT = """
            A short self check on the collections you use every day. You have ten minutes once you start, and you can start whenever you like before the due date.
            """;

    private static final Logger log = LoggerFactory.getLogger(QuizExerciseApi.class);

    private final QuizExerciseService quizExerciseService;

    private final QuizExerciseRepository quizExerciseRepository;

    public QuizExerciseApi(QuizExerciseService quizExerciseService, QuizExerciseRepository quizExerciseRepository) {
        this.quizExerciseService = quizExerciseService;
        this.quizExerciseRepository = quizExerciseRepository;
    }

    /**
     * Creates the demo quiz exercise in the given course if it does not exist yet.
     * <p>
     * The quiz uses {@link QuizMode#INDIVIDUAL}, so every student starts their own batch whenever they open it and gets the full working time. That keeps the quiz participatable
     * for as long as the demo instance exists, which a synchronized quiz would not: it would end once its duration has elapsed and seeding never revisits an existing exercise.
     *
     * @param course the demo course the exercise belongs to.
     */
    public void createDemo(Course course) {
        boolean exerciseExists = quizExerciseRepository.findByCourseIdWithCategories(course.getId()).stream().anyMatch(exercise -> DEMO_EXERCISE_TITLE.equals(exercise.getTitle()));
        if (exerciseExists) {
            log.debug("Demo quiz exercise already exists, skipping creation");
            return;
        }

        QuizExercise quizExercise = QuizExerciseFactory.generateQuizExercise(DEMO_EXERCISE_TITLE, DEMO_EXERCISE_SHORT_NAME, DEMO_PROBLEM_STATEMENT, ExerciseDates.ongoing(),
                QuizMode.INDIVIDUAL, DEMO_DURATION_SECONDS, course);
        quizExercise.getCategories().add("Java");

        quizExercise
                .addQuestion(QuizExerciseFactory.generateMultipleChoiceQuestion("Collection guarantees", "Which of the following statements about Java collections are correct?",
                        "ArrayList is backed by an array and therefore indexes in constant time, while LinkedList has to walk the chain. HashSet makes no ordering promise at all, "
                                + "LinkedHashSet is the one that preserves insertion order. TreeMap is a sorted map and keeps its keys in the order of their natural comparison.",
                        4.0, ScoringType.PROPORTIONAL_WITHOUT_PENALTY, false,
                        List.of(QuizExerciseFactory.generateAnswerOption("`ArrayList` gives you access by index in constant time.", true, "Backed by an array."),
                                QuizExerciseFactory.generateAnswerOption("`LinkedList` gives you access by index in constant time.", false, "It has to traverse the list."),
                                QuizExerciseFactory.generateAnswerOption("`HashSet` preserves the order in which elements were inserted.", false, "Use `LinkedHashSet` for that."),
                                QuizExerciseFactory.generateAnswerOption("`TreeMap` keeps its keys sorted.", true, "It is a `SortedMap`."))));

        quizExercise.addQuestion(QuizExerciseFactory.generateMultipleChoiceQuestion("Average lookup cost",
                "A `HashMap` uses a hash function that distributes the keys well. What is the average time complexity of `get`?",
                "With a good hash function the entries spread evenly across the buckets, so a lookup inspects only a handful of entries regardless of the map size.", 2.0,
                ScoringType.ALL_OR_NOTHING, true,
                List.of(QuizExerciseFactory.generateAnswerOption("O(1)", true, "Constant on average."),
                        QuizExerciseFactory.generateAnswerOption("O(log n)", false, "That is the cost of a balanced tree, for example `TreeMap`."),
                        QuizExerciseFactory.generateAnswerOption("O(n)", false, "That is the worst case, when all keys collide in the same bucket."),
                        QuizExerciseFactory.generateAnswerOption("O(n log n)", false, "That is the cost of sorting, not of a lookup."))));

        quizExercise.addQuestion(QuizExerciseFactory.generateShortAnswerQuestion("Complexity of map lookups",
                "Looking up a key in a `HashMap` costs [-spot 1] on average, while looking up a key in a `TreeMap` costs [-spot 2].",
                "A hash map reaches its bucket directly, a tree map descends a balanced search tree.", 2.0, 85, false, List.of("O(1)", "O(log n)")));

        // The achievable points of a quiz are the sum of its question points, so they can only be set once all questions have been added.
        quizExercise.setMaxPoints(quizExercise.getOverallQuizPoints());

        try {
            QuizExercise createdExercise = quizExerciseService.createQuizExercise(quizExercise, List.of(), false, null);
            log.info("Created demo quiz exercise '{}' with id {}", DEMO_EXERCISE_TITLE, createdExercise.getId());
        }
        catch (IOException exception) {
            // Only thrown while storing the images of drag and drop questions, which the demo quiz deliberately does not use.
            throw new UncheckedIOException("Could not create the demo quiz exercise", exception);
        }
    }
}
