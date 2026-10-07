package de.tum.cit.aet.artemis.modeling.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_DEMO_AND_SCHEDULING;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.service.ResourceLoaderService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.factories.ExerciseDates;
import de.tum.cit.aet.artemis.exercise.factories.ExerciseFactory;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseConfigurationService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.exercise.service.ParticipationService;
import de.tum.cit.aet.artemis.modeling.config.ModelingEnabled;
import de.tum.cit.aet.artemis.modeling.domain.DiagramType;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.modeling.repository.ModelingExerciseRepository;
import de.tum.cit.aet.artemis.modeling.service.ModelingSubmissionService;
import de.tum.cit.aet.artemis.notification.service.notifications.GroupNotificationScheduleService;

/**
 * Creates the modeling exercises of the demo course seeded by the {@code demo} profile.
 * <p>
 * Only exists on the node that seeds the demo data, so none of this is instantiated on a regular instance.
 */
@Conditional(ModelingEnabled.class)
@Controller
@Lazy
@Profile(PROFILE_DEMO_AND_SCHEDULING)
public class ModelingDemoApi extends AbstractModelingApi {

    /**
     * Title of the ongoing demo modeling exercise. Used as its idempotency key together with the course, so it must stay stable.
     */
    private static final String ONGOING_EXERCISE_TITLE = "Class Diagram: Library Management System";

    private static final String ONGOING_EXERCISE_SHORT_NAME = "demomodel";

    private static final String ONGOING_PROBLEM_STATEMENT = """
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

    /**
     * Title of the demo modeling exercise whose submissions wait for their assessment. Used as its idempotency key together with the course, so it must stay stable.
     */
    private static final String IN_ASSESSMENT_EXERCISE_TITLE = "Class Diagram: Online Shop";

    private static final String IN_ASSESSMENT_EXERCISE_SHORT_NAME = "demoshop";

    private static final String IN_ASSESSMENT_PROBLEM_STATEMENT = """
            # Class Diagram: Online Shop

            The online bookshop BookBarn is rebuilding its shop. Before the team starts, it needs a class diagram of the domain that everyone agrees on.

            From the requirements workshop we know the following:

            - **Customers** register with a name, an email address and a shipping address.
            - Every customer has exactly one **shopping cart** and can place any number of **orders**.
            - An order has an order number, the date it was placed and a status: `PLACED`, `PAID`, `SHIPPED` or `CANCELLED`.
            - An order consists of one or more **order items**. Each item refers to one **product** and records the quantity and the price paid, since prices change over time.
            - Every product has a name and a price. The shop sells printed **books**, which have a weight for shipping, and **e-books**, which have a download link.

            ## Your task

            Model this domain as a **UML class diagram**. Your diagram must contain:

            1. All classes named above, with the attributes from the workshop and sensible types.
            2. The associations between them, each with **multiplicities on both ends**.
            3. A generalization for the kinds of products, with an **abstract** superclass.
            4. An **enumeration** for the status of an order.
            5. A **composition** wherever a part cannot exist without its whole.

            ## Assessment

            | Criterion                               | Points |
            |-----------------------------------------|--------|
            | Classes with sensible attributes        | 3      |
            | Associations and multiplicities         | 3      |
            | Abstract product with books and e-books | 2      |
            | Enumeration and composition             | 2      |
            """;

    private static final String EXAM_EXERCISE_TITLE = "Class Diagram: Course Enrollment";

    private static final String EXAM_PROBLEM_STATEMENT = """
            # Class Diagram: Course Enrollment

            The university wants software to manage the enrollments in its courses. Model the domain as a **UML class diagram**:

            - A **student** has a matriculation number and a name.
            - A **course** has a title and a number of credits. It is taught by exactly one **lecturer**, who can teach several courses.
            - Students **enroll** in courses. An enrollment records the semester and, once the course is over, the grade.

            Your diagram must contain the classes with their attributes, the associations with **multiplicities on both ends**, and the enrollment modelled so that it can hold
            the semester and the grade.

            ## Assessment

            | Criterion                          | Points |
            |------------------------------------|--------|
            | Classes with sensible attributes   | 4      |
            | Associations and multiplicities    | 4      |
            | Enrollment with semester and grade | 2      |
            """;

    /**
     * The class diagrams the demo students submit to the exercise in assessment, handed out in turn: a complete solution and two with the gaps a tutor has to find. They are in the
     * format the modeling editor of the client saves.
     */
    private static final List<Path> IN_ASSESSMENT_SUBMISSION_MODELS = Stream
            .of("online-shop-complete.json", "online-shop-without-order-items.json", "online-shop-without-cart.json").map(fileName -> Path.of("demo", "modeling", fileName))
            .toList();

    private static final Logger log = LoggerFactory.getLogger(ModelingDemoApi.class);

    private final ModelingExerciseRepository modelingExerciseRepository;

    private final SubmissionRepository submissionRepository;

    private final ChannelService channelService;

    private final ExerciseConfigurationService exerciseConfigurationService;

    private final ExerciseVersionService exerciseVersionService;

    private final ParticipationService participationService;

    private final ModelingSubmissionService modelingSubmissionService;

    private final ResourceLoaderService resourceLoaderService;

    private final GroupNotificationScheduleService groupNotificationScheduleService;

    private final ExerciseService exerciseService;

    public ModelingDemoApi(ModelingExerciseRepository modelingExerciseRepository, SubmissionRepository submissionRepository, ChannelService channelService,
            ExerciseConfigurationService exerciseConfigurationService, ExerciseVersionService exerciseVersionService, ParticipationService participationService,
            ModelingSubmissionService modelingSubmissionService, ResourceLoaderService resourceLoaderService, GroupNotificationScheduleService groupNotificationScheduleService,
            ExerciseService exerciseService) {
        this.modelingExerciseRepository = modelingExerciseRepository;
        this.submissionRepository = submissionRepository;
        this.channelService = channelService;
        this.exerciseConfigurationService = exerciseConfigurationService;
        this.exerciseVersionService = exerciseVersionService;
        this.participationService = participationService;
        this.modelingSubmissionService = modelingSubmissionService;
        this.resourceLoaderService = resourceLoaderService;
        this.groupNotificationScheduleService = groupNotificationScheduleService;
        this.exerciseService = exerciseService;
    }

    /**
     * Creates the demo modeling exercises in the given course that do not exist yet.
     * <p>
     * One exercise is ongoing, see {@link ExerciseDates#ongoing()}, so that demo students can participate right away. In the other one every demo student has submitted a class
     * diagram, and the submissions wait for their assessment: it was released two weeks before seeding, closed once the demo students had submitted, and can be assessed until a
     * year after seeding, so that the submissions stay in the assessment queue of the demo tutor on a long-lived demo instance.
     *
     * @param course   the demo course the exercises belong to.
     * @param students the demo students, who submit to the exercise in assessment.
     * @return the ongoing exercise and the exercise in assessment, whether they already existed or were created by this call.
     */
    public List<ModelingExercise> createDemo(Course course, List<User> students) {
        List<ModelingExercise> existingExercises = modelingExerciseRepository.findByCourseIdWithCategories(course.getId());
        ModelingExercise ongoingExercise = findExisting(existingExercises, ONGOING_EXERCISE_TITLE)
                .orElseGet(() -> createExercise(course, ONGOING_EXERCISE_TITLE, ONGOING_EXERCISE_SHORT_NAME, ONGOING_PROBLEM_STATEMENT, ExerciseDates.ongoing()));
        ModelingExercise exerciseInAssessment = findExisting(existingExercises, IN_ASSESSMENT_EXERCISE_TITLE).orElseGet(() -> createExerciseInAssessment(course, students));
        return List.of(ongoingExercise, exerciseInAssessment);
    }

    private static Optional<ModelingExercise> findExisting(List<ModelingExercise> exercises, String title) {
        Optional<ModelingExercise> existingExercise = exercises.stream().filter(exercise -> title.equals(exercise.getTitle())).findFirst();
        if (existingExercise.isPresent()) {
            log.debug("Demo modeling exercise '{}' already exists, skipping creation", title);
        }
        return existingExercise;
    }

    /**
     * Creates the class diagram exercise of the demo test exam in the given exercise group. Like every exam exercise, it belongs to its exercise group instead of the course and
     * has no dates of its own: students work on it while they take the exam.
     *
     * @param exerciseGroup the exercise group of the demo test exam the exercise belongs to.
     * @return the created exercise.
     */
    public ModelingExercise createDemoExamExercise(ExerciseGroup exerciseGroup) {
        ModelingExercise modelingExercise = buildClassDiagramExercise(EXAM_EXERCISE_TITLE, null, EXAM_PROBLEM_STATEMENT, new ExerciseDates(null, null, null, null),
                exerciseGroup.getExam().getCourse());
        modelingExercise.setCourse(null);
        modelingExercise.setExerciseGroup(exerciseGroup);
        return create(modelingExercise);
    }

    private ModelingExercise createExercise(Course course, String title, String shortName, String problemStatement, ExerciseDates dates) {
        // No example solution model: a full Apollon diagram is not needed to participate, and an empty one would show up as a broken example solution.
        ModelingExercise modelingExercise = buildClassDiagramExercise(title, shortName, problemStatement, dates, course);
        modelingExercise.getCategories().add(ExerciseFactory.exerciseCategory("Modeling", "#9dca53"));
        return create(modelingExercise);
    }

    /**
     * Builds a class diagram exercise worth 10 points, like the modeling exercise editor of the client does.
     */
    private static ModelingExercise buildClassDiagramExercise(String title, @Nullable String shortName, String problemStatement, ExerciseDates dates, Course course) {
        ModelingExercise modelingExercise = ExerciseFactory.populateExercise(new ModelingExercise(), title, shortName, problemStatement, 10.0, 0.0, dates, course);
        modelingExercise.setDiagramType(DiagramType.ClassDiagram);
        return modelingExercise;
    }

    /**
     * Creates a class diagram exercise the way {@code ModelingExerciseResource#createModelingExercise} does, rather than saving the entity directly. An exercise of an exam gets
     * no channel, like in production: {@link ChannelService#createExerciseChannel} only creates channels for course exercises.
     */
    private ModelingExercise create(ModelingExercise modelingExercise) {
        modelingExercise.setAssessmentType(AssessmentType.MANUAL);
        modelingExercise.validateGeneralSettings();

        ModelingExercise createdExercise = modelingExerciseRepository.save(modelingExercise);
        // The configurations hold the key to their exercise, so their permanent rows are created right after it is stored, like the production creation path does. Without
        // requested settings they get the defaults, which are also the ones the exercise editor of the client sends.
        exerciseConfigurationService.initialize(createdExercise, modelingExercise.getTeamAssignmentConfig(), modelingExercise.getPlagiarismDetectionConfig());
        channelService.createExerciseChannel(createdExercise, Optional.empty());
        // Sends the release notification right away for a released exercise, and schedules the one about assessed submissions.
        groupNotificationScheduleService.checkNotificationsForNewExerciseAsync(createdExercise);
        exerciseVersionService.createExerciseVersion(createdExercise);

        log.info("Created demo modeling exercise '{}' with id {}", createdExercise.getTitle(), createdExercise.getId());
        return createdExercise;
    }

    /**
     * Creates the exercise in assessment: while it is open, every demo student submits one of {@link #IN_ASSESSMENT_SUBMISSION_MODELS}, then it is closed.
     */
    private ModelingExercise createExerciseInAssessment(Course course, List<User> students) {
        // Read before anything is created, so that a missing diagram does not leave an exercise without submissions behind.
        List<String> models = IN_ASSESSMENT_SUBMISSION_MODELS.stream().map(this::readModel).toList();
        ZonedDateTime now = ZonedDateTime.now();
        ModelingExercise exercise = createExercise(course, IN_ASSESSMENT_EXERCISE_TITLE, IN_ASSESSMENT_EXERCISE_SHORT_NAME, IN_ASSESSMENT_PROBLEM_STATEMENT,
                new ExerciseDates(now.minusWeeks(2), null, now.plusWeeks(1), now.plusYears(1)));
        for (int index = 0; index < students.size(); index++) {
            submitAsStudent(exercise, students.get(index), models.get(index % models.size()));
        }

        // Closes the exercise like its due date passing would, only after the last submission, so that every submission counts as handed in on time.
        // Stored like ModelingExerciseResource#updateModelingExercise stores a change: validated, then students are notified about changed dates and the change is recorded
        // as a new version.
        ModelingExercise closedExercise = modelingExerciseRepository.findByIdElseThrow(exercise.getId());
        ZonedDateTime originalReleaseDate = closedExercise.getReleaseDate();
        ZonedDateTime originalAssessmentDueDate = closedExercise.getAssessmentDueDate();
        closedExercise.setDueDate(ZonedDateTime.now());
        closedExercise.validateGeneralSettings();
        ModelingExercise savedExercise = modelingExerciseRepository.save(closedExercise);
        exerciseService.notifyAboutExerciseChanges(originalReleaseDate, originalAssessmentDueDate, savedExercise.getProblemStatement(), savedExercise, null);
        exerciseVersionService.createExerciseVersion(savedExercise);
        return savedExercise;
    }

    /**
     * Submits the given class diagram as the given student, the way the modeling editor of the client does: it starts the exercise, which creates an empty submission, and saves
     * the diagram into that submission through {@code ModelingSubmissionResource}.
     */
    private void submitAsStudent(ModelingExercise exercise, User student, String model) {
        SecurityUtils.runAs(student, () -> {
            StudentParticipation participation = participationService.startExercise(exercise, student, true);
            ModelingSubmission submission = new ModelingSubmission();
            submission.setId(submissionRepository.findLatestSubmissionByParticipationId(participation.getId()).orElseThrow().getId());
            submission.setModel(model);
            submission.setSubmitted(true);
            // Every request of the editor loads the exercise anew, and the submission path strips the exercise of what students must not see.
            modelingSubmissionService.handleModelingSubmission(submission, modelingExerciseRepository.findByIdElseThrow(exercise.getId()), student, null);
        });
    }

    private String readModel(Path path) {
        try {
            return resourceLoaderService.getResource(path).getContentAsString(StandardCharsets.UTF_8);
        }
        catch (IOException exception) {
            throw new UncheckedIOException("Could not read the demo class diagram " + path, exception);
        }
    }
}
