package de.tum.cit.aet.artemis.text.api;

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
import de.tum.cit.aet.artemis.text.config.TextEnabled;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.factories.TextExerciseFactory;
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;

/**
 * Creates the text exercises of the demo course seeded by the {@code demo} profile.
 * <p>
 * Only exists on the node that seeds the demo data, so none of this is instantiated on a regular instance.
 */
@Conditional(TextEnabled.class)
@Controller
@Lazy
@Profile(PROFILE_DEMO_AND_SCHEDULING)
public class TextDemoApi extends AbstractTextApi {

    /**
     * Title of the demo text exercise. Used as the idempotency key of {@link #createDemo(Course)} together with the course, so it must stay stable.
     */
    private static final String DEMO_EXERCISE_TITLE = "Essay: Monolith or Microservices?";

    private static final String DEMO_EXERCISE_SHORT_NAME = "demotext";

    private static final String DEMO_PROBLEM_STATEMENT = """
            # Essay: Monolith or Microservices?

            The fictional online bookshop **BookBarn** runs its entire platform as a single Spring Boot application. Catalogue, checkout, invoicing and the recommendation engine all
            live in one code base and are deployed together. The team has grown from 4 to 25 developers in two years, and they now release once every three weeks because every
            change requires a full regression test of the whole system.

            Management has asked whether BookBarn should migrate to microservices.

            ## Your task

            Write an essay of **600 to 800 words** that gives a clear recommendation. Do not sit on the fence: decide, and defend your decision.

            Your essay must address all of the following:

            1. **The actual problem.** Which of BookBarn's symptoms are caused by the architecture, and which are caused by their process? Be specific.
            2. **The case for splitting.** Name at least two concrete boundaries along which you would split the system, and explain why those boundaries and not others.
            3. **The cost.** Discuss at least three costs a migration would introduce, for example distributed transactions, operational complexity, or network failure modes.
            4. **An alternative.** Describe one change that would address the symptoms *without* moving to microservices, and explain when you would prefer it.
            5. **Your recommendation.** State what BookBarn should do in the next six months.

            ## What we are looking for

            - A clear position supported by reasoning, not a list of textbook definitions.
            - Concrete references to BookBarn's situation rather than generic advice.
            - Honest treatment of the trade-offs of the option you recommend.

            ## Assessment

            | Criterion                                   | Points |
            |---------------------------------------------|--------|
            | Problem analysis (process vs. architecture) | 2      |
            | Proposed service boundaries                 | 2      |
            | Discussion of migration costs               | 3      |
            | Alternative to a migration                  | 2      |
            | Clarity and structure                       | 1      |
            """;

    private static final String DEMO_EXAMPLE_SOLUTION = """
            A strong answer recognises that most of BookBarn's pain is caused by their release process rather than by the monolith itself: a three week release train and a full
            regression suite are organisational choices, and both can be improved without distributing the system.

            It then identifies boundaries that follow business capabilities with genuinely different change rates and scaling needs, typically invoicing (stable, compliance driven)
            and recommendations (experimental, compute heavy), while arguing that catalogue and checkout share too much data to separate cheaply.

            On costs, it names the shift from local transactions to eventual consistency, the operational burden of running and observing many services with a team of 25, and the
            new class of partial failures that has to be handled explicitly in code.

            As an alternative it proposes modularising the monolith first, enforcing module boundaries in the build and introducing independent deployability only where measurement
            shows it is needed.

            The recommendation is to keep the monolith for now, invest in test and deployment automation, and extract the recommendation engine as the first service.
            """;

    private static final Logger log = LoggerFactory.getLogger(TextDemoApi.class);

    private final TextExerciseRepository textExerciseRepository;

    private final ChannelService channelService;

    private final ExerciseConfigurationService exerciseConfigurationService;

    public TextDemoApi(TextExerciseRepository textExerciseRepository, ChannelService channelService, ExerciseConfigurationService exerciseConfigurationService) {
        this.textExerciseRepository = textExerciseRepository;
        this.channelService = channelService;
        this.exerciseConfigurationService = exerciseConfigurationService;
    }

    /**
     * Creates the demo text exercise in the given course if it does not exist yet.
     * <p>
     * The exercise is currently ongoing, see {@link ExerciseDates#ongoing()}, so that demo students can participate right away. This mirrors the production creation path of
     * {@code TextExerciseCreationUpdateResource} rather than saving the entity directly.
     *
     * @param course the demo course the exercise belongs to.
     */
    public void createDemo(Course course) {
        boolean exerciseExists = textExerciseRepository.findByCourseIdWithCategories(course.getId()).stream().anyMatch(exercise -> DEMO_EXERCISE_TITLE.equals(exercise.getTitle()));
        if (exerciseExists) {
            log.debug("Demo text exercise already exists, skipping creation");
            return;
        }

        TextExercise textExercise = TextExerciseFactory.generateTextExercise(DEMO_EXERCISE_TITLE, DEMO_EXERCISE_SHORT_NAME, DEMO_PROBLEM_STATEMENT, 10.0, 0.0,
                ExerciseDates.ongoing(), DEMO_EXAMPLE_SOLUTION, course);
        textExercise.setAssessmentType(AssessmentType.MANUAL);
        textExercise.getCategories().add("Architecture");
        textExercise.validateGeneralSettings();

        TextExercise createdExercise = textExerciseRepository.save(textExercise);
        // The configurations hold the key to their exercise, so their permanent rows are created right after it is stored, like the production creation path does.
        exerciseConfigurationService.initialize(createdExercise, textExercise.getTeamAssignmentConfig(), textExercise.getPlagiarismDetectionConfig());
        channelService.createExerciseChannel(createdExercise, Optional.empty());

        log.info("Created demo text exercise '{}' with id {}", DEMO_EXERCISE_TITLE, createdExercise.getId());
    }
}
