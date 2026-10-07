package de.tum.cit.aet.artemis.fileupload.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_DEMO_AND_SCHEDULING;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.factories.ExerciseDates;
import de.tum.cit.aet.artemis.exercise.service.ExerciseConfigurationService;
import de.tum.cit.aet.artemis.fileupload.config.FileUploadEnabled;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.factories.FileUploadExerciseFactory;
import de.tum.cit.aet.artemis.fileupload.repository.FileUploadExerciseRepository;

/**
 * General-purpose API for file upload exercises (but not for general upload functionality).
 */
/**
 * Creates the file upload exercise of the demo course seeded by the {@code demo} profile.
 * <p>
 * Only exists on the node that seeds the demo data, so none of this is instantiated on a regular instance.
 */
@Conditional(FileUploadEnabled.class)
@Controller
@Lazy
@Profile(PROFILE_DEMO_AND_SCHEDULING)
public class FileUploadDemoApi extends AbstractFileModuleApi {

    /**
     * Title of the demo file upload exercise. Used as the idempotency key of {@link #createDemo(Course)} together with the course, so it must stay stable.
     */
    private static final String DEMO_EXERCISE_TITLE = "Poster: Comparing Sorting Algorithms";

    private static final String DEMO_EXERCISE_SHORT_NAME = "demoupload";

    private static final String DEMO_FILE_PATTERN = "pdf,png";

    private static final String DEMO_PROBLEM_STATEMENT = """
            # Poster: Comparing Sorting Algorithms

            Explaining an algorithm to someone else is the fastest way to find out whether you have understood it yourself. In this exercise you do not write code, you explain.

            ## Your task

            Design a **single page poster** that compares these four sorting algorithms:

            - Insertion sort
            - Merge sort
            - Quicksort
            - Heapsort

            Your poster has to answer three questions for each algorithm:

            1. **How does it work?** One diagram or a short worked example on the array `[5, 2, 9, 1, 5, 6]`. A picture beats a paragraph here.
            2. **What does it cost?** Best, average and worst case time complexity, plus the space complexity. State whether the algorithm is stable and whether it sorts in place.
            3. **When would you pick it?** One concrete situation in which this algorithm is the right choice, and one in which it clearly is not.

            Finish with a short section that answers the question: *why does the standard library of your favourite language not simply use quicksort for everything?*

            ## Formal requirements

            - Exactly **one page**, portrait or landscape.
            - Submit as **PDF or PNG**. Other formats are rejected by the upload.
            - Readable at 100% zoom. If we have to zoom in to read your complexity table, it is too small.
            - Sources for anything you did not work out yourself, in a corner of the poster.

            ## Assessment

            | Criterion                                  | Points |
            |--------------------------------------------|--------|
            | Correct complexities and properties         | 4      |
            | Quality of the diagrams or worked examples  | 3      |
            | Justified use cases                         | 2      |
            | Layout and readability                      | 1      |
            """;

    private static final String DEMO_EXAMPLE_SOLUTION = """
            A strong poster shows one worked pass per algorithm on the given array rather than restating pseudo code, gets the worst case of quicksort right (O(n^2), not O(n log n)),
            and notes that only insertion sort and merge sort are stable while insertion sort, quicksort and heapsort sort in place.

            The closing section should mention that library sorts are hybrids: they switch to insertion sort for small partitions and fall back to a guaranteed O(n log n) algorithm
            when quicksort degenerates, and that stability is a documented guarantee some languages have to keep.
            """;

    private static final Logger log = LoggerFactory.getLogger(FileUploadDemoApi.class);

    private final FileUploadExerciseRepository fileUploadExerciseRepository;

    private final ChannelService channelService;

    private final ExerciseConfigurationService exerciseConfigurationService;

    public FileUploadDemoApi(FileUploadExerciseRepository fileUploadExerciseRepository, ChannelService channelService, ExerciseConfigurationService exerciseConfigurationService) {
        this.fileUploadExerciseRepository = fileUploadExerciseRepository;
        this.channelService = channelService;
        this.exerciseConfigurationService = exerciseConfigurationService;
    }

    /**
     * Creates the demo file upload exercise in the given course if it does not exist yet.
     * <p>
     * The exercise is currently ongoing, see {@link ExerciseDates#ongoing()}, so that demo students can participate right away. This mirrors the production creation path of
     * {@code FileUploadExerciseResource} rather than saving the entity directly, including that file upload exercises are always assessed manually.
     *
     * @param course the demo course the exercise belongs to.
     */
    public void createDemo(Course course) {
        boolean exerciseExists = fileUploadExerciseRepository.findByCourseIdWithCategories(course.getId()).stream()
                .anyMatch(exercise -> DEMO_EXERCISE_TITLE.equals(exercise.getTitle()));
        if (exerciseExists) {
            log.debug("Demo file upload exercise already exists, skipping creation");
            return;
        }

        FileUploadExercise fileUploadExercise = FileUploadExerciseFactory.generateFileUploadExercise(DEMO_EXERCISE_TITLE, DEMO_EXERCISE_SHORT_NAME, DEMO_PROBLEM_STATEMENT, 10.0,
                0.0, ExerciseDates.ongoing(), DEMO_FILE_PATTERN, DEMO_EXAMPLE_SOLUTION, course);
        // File upload exercises are always assessed manually, see FileUploadExerciseResource#createFileUploadExercise.
        fileUploadExercise.setAssessmentType(AssessmentType.MANUAL);
        fileUploadExercise.getCategories().add("Algorithms");
        fileUploadExercise.validateGeneralSettings();

        FileUploadExercise createdExercise = fileUploadExerciseRepository.save(fileUploadExercise);
        // The configurations hold the key to their exercise, so their permanent rows are created right after it is stored, like the production creation path does.
        exerciseConfigurationService.initialize(createdExercise, fileUploadExercise.getTeamAssignmentConfig(), fileUploadExercise.getPlagiarismDetectionConfig());
        channelService.createExerciseChannel(createdExercise, Optional.empty());

        log.info("Created demo file upload exercise '{}' with id {}", DEMO_EXERCISE_TITLE, createdExercise.getId());
    }
}
