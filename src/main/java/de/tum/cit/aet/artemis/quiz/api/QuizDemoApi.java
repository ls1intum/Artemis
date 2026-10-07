package de.tum.cit.aet.artemis.quiz.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_DEMO_AND_SCHEDULING;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.api.AbstractApi;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.factories.ExerciseDates;
import de.tum.cit.aet.artemis.exercise.factories.ExerciseFactory;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.exercise.service.ParticipationService;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ScoringType;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;
import de.tum.cit.aet.artemis.quiz.dto.submission.QuizSubmissionFromLiveClientDTO;
import de.tum.cit.aet.artemis.quiz.dto.submittedanswer.EntityIdRefDTO;
import de.tum.cit.aet.artemis.quiz.dto.submittedanswer.MultipleChoiceSubmittedAnswerFromLiveClientDTO;
import de.tum.cit.aet.artemis.quiz.dto.submittedanswer.ShortAnswerSubmittedAnswerFromLiveClientDTO;
import de.tum.cit.aet.artemis.quiz.dto.submittedanswer.ShortAnswerSubmittedTextFromLiveClientDTO;
import de.tum.cit.aet.artemis.quiz.dto.submittedanswer.SubmittedAnswerFromLiveClientDTO;
import de.tum.cit.aet.artemis.quiz.exception.QuizJoinException;
import de.tum.cit.aet.artemis.quiz.exception.QuizSubmissionException;
import de.tum.cit.aet.artemis.quiz.factories.QuizExerciseFactory;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.quiz.service.QuizBatchService;
import de.tum.cit.aet.artemis.quiz.service.QuizExerciseService;
import de.tum.cit.aet.artemis.quiz.service.QuizResultService;
import de.tum.cit.aet.artemis.quiz.service.QuizSubmissionService;

/**
 * Creates the quiz exercises of the demo course seeded by the {@code demo} profile.
 * <p>
 * Only exists on the node that seeds the demo data, so none of this is instantiated on a regular instance.
 */
@Controller
@Lazy
@Profile(PROFILE_DEMO_AND_SCHEDULING)
public class QuizDemoApi implements AbstractApi {

    /**
     * Title of the ongoing demo quiz. Used as its idempotency key together with the course, so it must stay stable.
     */
    private static final String ONGOING_QUIZ_TITLE = "Quiz: Java Collections and Complexity";

    private static final String ONGOING_QUIZ_SHORT_NAME = "demoquiz";

    private static final String ONGOING_PROBLEM_STATEMENT = """
            A short self check on the collections you use every day. You have ten minutes once you start, and you can start whenever you like before the due date.
            """;

    /**
     * Title of the demo quiz that has ended. Used as its idempotency key together with the course, so it must stay stable.
     */
    private static final String ENDED_QUIZ_TITLE = "Quiz: Sorting and Searching";

    private static final String ENDED_QUIZ_SHORT_NAME = "demosortquiz";

    private static final String ENDED_PROBLEM_STATEMENT = """
            A short self check on the sorting and searching algorithms of the lecture. The quiz has ended: look at your result, and practice it as often as you like.
            """;

    /**
     * Working time of the demo quizzes in seconds.
     */
    private static final int DEMO_DURATION_SECONDS = 600;

    /**
     * The answers the demo students hand in to the quiz that has ended, one sheet per student in turn, so that its results and statistics show the whole range from guessed to
     * perfect.
     */
    private static final List<AnswerSheet> ENDED_QUIZ_ANSWER_SHEETS = List.of(
            // believes quicksort is fast even in the worst case: 9 of 10 points
            new AnswerSheet(List.of(Set.of(0, 1, 2), Set.of(1), Set.of(0)), List.of("pivot", "O(n log n)")),
            // everything right: 10 points
            new AnswerSheet(List.of(Set.of(0, 1), Set.of(1), Set.of(0)), List.of("pivot", "O(n log n)")),
            // mixes up binary and linear search, and stable and unstable sorting: 5 points
            new AnswerSheet(List.of(Set.of(0, 1), Set.of(2), Set.of(1)), List.of("pivot", "O(n^2)")),
            // mostly guessed: 2 points
            new AnswerSheet(List.of(Set.of(2, 3), Set.of(0), Set.of(0)), List.of("median", "O(n)")));

    private static final Logger log = LoggerFactory.getLogger(QuizDemoApi.class);

    private final QuizExerciseService quizExerciseService;

    private final QuizExerciseRepository quizExerciseRepository;

    private final ExerciseVersionService exerciseVersionService;

    private final ParticipationService participationService;

    private final QuizBatchService quizBatchService;

    private final QuizSubmissionService quizSubmissionService;

    private final QuizResultService quizResultService;

    public QuizDemoApi(QuizExerciseService quizExerciseService, QuizExerciseRepository quizExerciseRepository, ExerciseVersionService exerciseVersionService,
            ParticipationService participationService, QuizBatchService quizBatchService, QuizSubmissionService quizSubmissionService, QuizResultService quizResultService) {
        this.quizExerciseService = quizExerciseService;
        this.quizExerciseRepository = quizExerciseRepository;
        this.exerciseVersionService = exerciseVersionService;
        this.participationService = participationService;
        this.quizBatchService = quizBatchService;
        this.quizSubmissionService = quizSubmissionService;
        this.quizResultService = quizResultService;
    }

    /**
     * Creates the demo quizzes in the given course that do not exist yet.
     * <p>
     * Both quizzes use {@link QuizMode#INDIVIDUAL}, so every student starts their own batch whenever they open one and gets the full working time. That keeps the ongoing quiz
     * participatable for as long as the demo instance exists, which a synchronized quiz would not: it would end once its duration has elapsed and seeding never revisits an
     * existing exercise. The other quiz was released a week before seeding, ends during seeding once every demo student has taken it, and is evaluated right away. As an ended
     * quiz it is open for practice, and its questions fill the Training tab of the course.
     *
     * @param course   the demo course the quizzes belong to.
     * @param students the demo students, who take the quiz that has ended.
     * @return the ongoing quiz and the quiz that has ended, whether they already existed or were created by this call.
     */
    public List<QuizExercise> createDemo(Course course, List<User> students) {
        List<QuizExercise> existingQuizzes = quizExerciseRepository.findByCourseIdWithCategories(course.getId());
        QuizExercise ongoingQuiz = findExisting(existingQuizzes, ONGOING_QUIZ_TITLE).orElseGet(() -> createOngoingQuiz(course));
        QuizExercise endedQuiz = findExisting(existingQuizzes, ENDED_QUIZ_TITLE).orElseGet(() -> createEndedQuiz(course, students));
        return List.of(ongoingQuiz, endedQuiz);
    }

    private static Optional<QuizExercise> findExisting(List<QuizExercise> quizzes, String title) {
        Optional<QuizExercise> existingQuiz = quizzes.stream().filter(quiz -> title.equals(quiz.getTitle())).findFirst();
        if (existingQuiz.isPresent()) {
            log.debug("Demo quiz exercise '{}' already exists, skipping creation", title);
        }
        return existingQuiz;
    }

    private QuizExercise createOngoingQuiz(Course course) {
        QuizExercise quizExercise = QuizExerciseFactory.generateQuizExercise(ONGOING_QUIZ_TITLE, ONGOING_QUIZ_SHORT_NAME, ONGOING_PROBLEM_STATEMENT, ExerciseDates.ongoing(),
                QuizMode.INDIVIDUAL, DEMO_DURATION_SECONDS, course);
        quizExercise.getCategories().add(ExerciseFactory.exerciseCategory("Java", "#6ae8ac"));

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

        return create(quizExercise);
    }

    /**
     * Creates the quiz that has ended: while it runs, every demo student takes it with one of {@link #ENDED_QUIZ_ANSWER_SHEETS}, then it is ended and evaluated.
     * <p>
     * The answer sheets refer to the questions by their order and to the options by their index, so they have to change together with the questions.
     */
    private QuizExercise createEndedQuiz(Course course, List<User> students) {
        // No due date while the demo students take it: a due date would schedule the evaluation of the quiz for when it passes, which would evaluate it a second time after it is
        // ended and evaluated below.
        QuizExercise quizExercise = QuizExerciseFactory.generateQuizExercise(ENDED_QUIZ_TITLE, ENDED_QUIZ_SHORT_NAME, ENDED_PROBLEM_STATEMENT,
                new ExerciseDates(ZonedDateTime.now().minusWeeks(1), null, null, null), QuizMode.INDIVIDUAL, DEMO_DURATION_SECONDS, course);
        quizExercise.getCategories().add(ExerciseFactory.exerciseCategory("Algorithms", "#1b97ca"));

        quizExercise.addQuestion(QuizExerciseFactory.generateMultipleChoiceQuestion("Worst-case sorting",
                "Which of the following algorithms sort an array of n elements in O(n log n) time **in the worst case**?",
                "Merge sort always splits its input into halves, and heap sort always removes n elements from a heap of height log n. Quicksort degrades to O(n²) when its pivots "
                        + "split the input badly, and insertion sort needs O(n²) comparisons for an input in reverse order.",
                4.0, ScoringType.PROPORTIONAL_WITHOUT_PENALTY, false,
                List.of(QuizExerciseFactory.generateAnswerOption("Merge sort", true, "It always splits its input into two halves."),
                        QuizExerciseFactory.generateAnswerOption("Heap sort", true, "Each of the n removals from the heap costs O(log n)."),
                        QuizExerciseFactory.generateAnswerOption("Quicksort", false, "Bad pivots make it quadratic, only its average case is O(n log n)."),
                        QuizExerciseFactory.generateAnswerOption("Insertion sort", false, "It is quadratic unless the input is almost sorted."))));

        quizExercise.addQuestion(QuizExerciseFactory.generateMultipleChoiceQuestion("Binary search",
                "What is the worst-case time complexity of binary search in a sorted array of n elements?",
                "Every comparison halves the range that can still contain the element, so after log n steps only one candidate is left.", 2.0, ScoringType.ALL_OR_NOTHING, true,
                List.of(QuizExerciseFactory.generateAnswerOption("O(1)", false, "Only if the element happens to be in the middle."),
                        QuizExerciseFactory.generateAnswerOption("O(log n)", true, "Each step halves the search range."),
                        QuizExerciseFactory.generateAnswerOption("O(n)", false, "That is linear search, which does not need a sorted array."),
                        QuizExerciseFactory.generateAnswerOption("O(n log n)", false, "That is the cost of sorting the array first."))));

        quizExercise.addQuestion(QuizExerciseFactory.generateMultipleChoiceQuestion("Stable sorting",
                "A sorting algorithm is *stable* if elements with equal keys keep their relative order. Which of these algorithms is stable in its usual implementation?",
                "Merge sort takes from the left half first when two keys are equal, which keeps their order. The usual in-place implementations of the others swap elements over "
                        + "long distances and can reorder equal keys.",
                2.0, ScoringType.ALL_OR_NOTHING, true,
                List.of(QuizExerciseFactory.generateAnswerOption("Merge sort", true, "Equal keys are taken from the left half first."),
                        QuizExerciseFactory.generateAnswerOption("Quicksort", false, "Partitioning swaps elements past equal keys."),
                        QuizExerciseFactory.generateAnswerOption("Heap sort", false, "Building the heap reorders equal keys."),
                        QuizExerciseFactory.generateAnswerOption("Selection sort", false, "Swapping the minimum to the front can jump over equal keys."))));

        quizExercise.addQuestion(QuizExerciseFactory.generateShortAnswerQuestion("Quicksort",
                "Quicksort partitions the array around a [-spot 1] element. If that element splits the array evenly every time, quicksort runs in [-spot 2] time.",
                "Partitioning costs O(n) on every level of the recursion, and even splits lead to log n levels.", 2.0, 85, false, List.of("pivot", "O(n log n)")));

        QuizExercise createdQuiz = create(quizExercise);
        QuizExercise quizWithQuestions = quizExerciseRepository.findByIdWithQuestionsElseThrow(createdQuiz.getId());
        for (int index = 0; index < students.size(); index++) {
            takeQuiz(quizWithQuestions, students.get(index), ENDED_QUIZ_ANSWER_SHEETS.get(index % ENDED_QUIZ_ANSWER_SHEETS.size()));
        }

        // Ends the quiz once everyone has handed it in, like the end-now action of QuizExerciseResource, and evaluates it like QuizExerciseEvaluationResource, which gives every
        // participation its rated result and the quiz its statistics.
        quizExerciseRepository.updateDueDate(createdQuiz.getId(), ZonedDateTime.now());
        quizResultService.evaluateQuiz(createdQuiz.getId());
        return quizExerciseRepository.findByIdElseThrow(createdQuiz.getId());
    }

    /**
     * Creates the quiz like {@code QuizExerciseCreationUpdateResource#createCourseQuizExercise}, apart from notifying AtlasML, whose competency suggestions the demo course does
     * not need.
     */
    private QuizExercise create(QuizExercise quizExercise) {
        // The achievable points of a quiz are the sum of its question points, so they can only be set once all questions have been added.
        quizExercise.setMaxPoints(quizExercise.getOverallQuizPoints());
        try {
            QuizExercise createdQuiz = quizExerciseService.createQuizExercise(quizExercise, List.of(), false, null);
            exerciseVersionService.createExerciseVersion(createdQuiz);
            log.info("Created demo quiz exercise '{}' with id {}", createdQuiz.getTitle(), createdQuiz.getId());
            return createdQuiz;
        }
        catch (IOException exception) {
            // Only thrown while storing the images of drag and drop questions, which the demo quizzes deliberately do not use.
            throw new UncheckedIOException("Could not create the demo quiz exercise " + quizExercise.getTitle(), exception);
        }
    }

    /**
     * Takes the quiz as the given student, in the order in which the quiz page of the client calls the server: opening the quiz starts the participation, which creates an empty
     * submission ({@code QuizParticipationResource}), starting it creates and joins the student's own batch ({@code QuizExerciseBatchResource}), and handing it in submits the
     * answers ({@code QuizSubmissionResource}).
     */
    private void takeQuiz(QuizExercise quizExercise, User student, AnswerSheet answers) {
        SecurityUtils.runAs(student.getLogin(), () -> {
            participationService.startExercise(quizExercise, student, true);
            try {
                quizBatchService.joinBatch(quizExercise, student, null);
                quizSubmissionService.saveSubmissionForLiveMode(quizExercise.getId(), answers.toSubmission(quizExercise), student, true);
            }
            catch (QuizJoinException | QuizSubmissionException exception) {
                throw new IllegalStateException("Demo student " + student.getLogin() + " could not take the demo quiz", exception);
            }
        });
    }

    /**
     * The answers of one demo student.
     *
     * @param selectedOptions the indices of the options selected in each multiple choice question, in the order of the questions.
     * @param spotTexts       the texts entered into the spots of the short answer question, in the order of the spot numbers.
     */
    private record AnswerSheet(List<Set<Integer>> selectedOptions, List<String> spotTexts) {

        /**
         * Builds the submission the quiz page of the client posts, which refers to questions, options and spots by their ids.
         */
        private QuizSubmissionFromLiveClientDTO toSubmission(QuizExercise quizExercise) {
            Set<SubmittedAnswerFromLiveClientDTO> answers = new HashSet<>();
            Iterator<Set<Integer>> selections = selectedOptions.iterator();
            for (QuizQuestion question : quizExercise.getQuizQuestions()) {
                EntityIdRefDTO questionReference = new EntityIdRefDTO(question.getId());
                if (question instanceof MultipleChoiceQuestion multipleChoiceQuestion) {
                    Set<EntityIdRefDTO> options = selections.next().stream().map(index -> new EntityIdRefDTO(multipleChoiceQuestion.getAnswerOptions().get(index).getId()))
                            .collect(Collectors.toSet());
                    answers.add(new MultipleChoiceSubmittedAnswerFromLiveClientDTO(questionReference, options));
                }
                else if (question instanceof ShortAnswerQuestion shortAnswerQuestion) {
                    Set<ShortAnswerSubmittedTextFromLiveClientDTO> texts = shortAnswerQuestion.getSpots().stream()
                            .map(spot -> new ShortAnswerSubmittedTextFromLiveClientDTO(spotTexts.get(spot.getSpotNr() - 1), new EntityIdRefDTO(spot.getId())))
                            .collect(Collectors.toSet());
                    answers.add(new ShortAnswerSubmittedAnswerFromLiveClientDTO(questionReference, texts));
                }
            }
            return new QuizSubmissionFromLiveClientDTO(null, answers);
        }
    }
}
