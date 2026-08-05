package de.tum.cit.aet.artemis.modeling.api;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.factories.ExerciseDates;
import de.tum.cit.aet.artemis.exercise.service.ExerciseConfigurationService;
import de.tum.cit.aet.artemis.modeling.config.ModelingEnabled;
import de.tum.cit.aet.artemis.modeling.domain.DiagramType;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.factories.ModelingExerciseFactory;
import de.tum.cit.aet.artemis.modeling.repository.ModelingExerciseRepository;

/**
 * API for modeling exercise functionality that other modules need to access.
 */
@Conditional(ModelingEnabled.class)
@Controller
@Lazy
public class ModelingApi extends AbstractModelingApi {

    /**
     * Title of the demo modeling exercise. Used as the idempotency key of {@link #createDemo(Course)} together with the course, so it must stay stable.
     */
    private static final String DEMO_EXERCISE_TITLE = "Class Diagram: Library Management System";

    private static final String DEMO_EXERCISE_SHORT_NAME = "demomodel";

    private static final String DEMO_PROBLEM_STATEMENT = """
            # Class Diagram: Library Management System

            The city library wants to replace its card index with software. Before anyone writes code, the library needs a class diagram that everyone agrees on.

            From the interviews with the librarians we know the following:

            - The library owns **books**. A book is identified by its ISBN and has a title, an author and a year of publication.
            - The library can own **several physical copies** of the same book. Each copy has an inventory number and a condition (`NEW`, `GOOD`, `WORN`).
            - **Members** have a membership number, a name and an email address. A membership can be active or suspended.
            - A member borrows a **copy**, never a book in the abstract. A loan records the date it started and the date it is due back, and later the date it was returned.
            - A member may have at most **five open loans** at a time.
            - **Librarians** register new members and record returns. Every librarian is identified by a staff number.

            ## Your task

            Model this domain as a **UML class diagram**. Your diagram must contain:

            1. All classes named above, with the attributes mentioned in the interviews. Choose sensible types.
            2. The associations between them, each with **multiplicities on both ends**.
            3. The distinction between a book and a copy, modelled explicitly.
            4. An enumeration for the condition of a copy.
            5. At least two operations on the classes that need them, for example returning a copy or checking whether a member may borrow.

            ## Hints

            - The rule "at most five open loans" cannot be expressed with a multiplicity alone. Add it as a note or a constraint on the association.
            - Think about whether a loan is an association class or a class in its own right, and be ready to justify your choice.
            - Do not model the user interface or the database, only the domain.

            ## Assessment

            | Criterion                                    | Points |
            |----------------------------------------------|--------|
            | All required classes with sensible attributes | 3      |
            | Correct associations and multiplicities       | 3      |
            | Book and copy modelled as separate concepts   | 2      |
            | Enumeration and operations                    | 2      |
            """;

    private static final Logger log = LoggerFactory.getLogger(ModelingApi.class);

    private final ModelingExerciseRepository modelingExerciseRepository;

    private final ChannelService channelService;

    private final ExerciseConfigurationService exerciseConfigurationService;

    public ModelingApi(ModelingExerciseRepository modelingExerciseRepository, ChannelService channelService, ExerciseConfigurationService exerciseConfigurationService) {
        this.modelingExerciseRepository = modelingExerciseRepository;
        this.channelService = channelService;
        this.exerciseConfigurationService = exerciseConfigurationService;
    }

    /**
     * Creates the demo modeling exercise in the given course if it does not exist yet.
     * <p>
     * The exercise is currently ongoing, see {@link ExerciseDates#ongoing()}, so that demo students can participate right away. This mirrors the production creation path of
     * {@code ModelingExerciseResource} rather than saving the entity directly.
     *
     * @param course the demo course the exercise belongs to.
     */
    public void createDemo(Course course) {
        boolean exerciseExists = modelingExerciseRepository.findByCourseIdWithCategories(course.getId()).stream()
                .anyMatch(exercise -> DEMO_EXERCISE_TITLE.equals(exercise.getTitle()));
        if (exerciseExists) {
            log.debug("Demo modeling exercise already exists, skipping creation");
            return;
        }

        // No example solution model: a full Apollon diagram is not needed to participate, and an empty one would show up as a broken example solution.
        ModelingExercise modelingExercise = ModelingExerciseFactory.generateModelingExercise(DEMO_EXERCISE_TITLE, DEMO_EXERCISE_SHORT_NAME, DEMO_PROBLEM_STATEMENT, 10.0, 0.0,
                ExerciseDates.ongoing(), DiagramType.ClassDiagram, null, null, course);
        modelingExercise.setAssessmentType(AssessmentType.MANUAL);
        modelingExercise.getCategories().add("Modeling");
        modelingExercise.validateGeneralSettings();

        ModelingExercise createdExercise = modelingExerciseRepository.save(modelingExercise);
        // The configurations hold the key to their exercise, so their permanent rows are created right after it is stored, like the production creation path does.
        exerciseConfigurationService.initialize(createdExercise, modelingExercise.getTeamAssignmentConfig(), modelingExercise.getPlagiarismDetectionConfig());
        channelService.createExerciseChannel(createdExercise, Optional.empty());

        log.info("Created demo modeling exercise '{}' with id {}", DEMO_EXERCISE_TITLE, createdExercise.getId());
    }
}
