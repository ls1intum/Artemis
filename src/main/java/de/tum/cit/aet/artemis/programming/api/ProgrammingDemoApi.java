package de.tum.cit.aet.artemis.programming.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_DEMO_AND_SCHEDULING;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.api.AbstractApi;
import de.tum.cit.aet.artemis.core.service.ResourceLoaderService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.factories.ExerciseDates;
import de.tum.cit.aet.artemis.localci.service.ci.ContinuousIntegrationService;
import de.tum.cit.aet.artemis.localvc.service.vcs.VersionControlService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.factories.ProgrammingExerciseFactory;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseCreationUpdateService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseValidationService;

/**
 * Creates the programming exercise of the demo course seeded by the {@code demo} profile.
 * <p>
 * Only exists on the node that seeds the demo data, so none of this is instantiated on a regular instance.
 */
@Controller
@Lazy
@Profile(PROFILE_DEMO_AND_SCHEDULING)
public class ProgrammingDemoApi implements AbstractApi {

    /**
     * Short name of the demo programming exercise. Used as the idempotency key of {@link #createDemo(Course)} together with the course, so it must stay stable.
     */
    private static final String DEMO_EXERCISE_SHORT_NAME = "demoprog";

    private static final String DEMO_EXERCISE_TITLE = "Sorting Algorithms in Java";

    private static final ProgrammingLanguage DEMO_PROGRAMMING_LANGUAGE = ProgrammingLanguage.JAVA;

    private static final ProjectType DEMO_PROJECT_TYPE = ProjectType.PLAIN_MAVEN;

    private static final String DEMO_PACKAGE_NAME = "de.tum.cit.aet.artemis.demo";

    /**
     * Fallback problem statement, used when the template readme of the demo programming language cannot be read. The template repositories themselves still contain the actual
     * assignment, so the exercise stays usable.
     */
    private static final String DEMO_FALLBACK_PROBLEM_STATEMENT = """
            # Sorting Algorithms

            Implement the sorting strategies in the template repository and make the provided tests pass. Start by opening the exercise in the online editor or by cloning the
            repository, then work through the classes that are marked as to do.
            """;

    private static final Logger log = LoggerFactory.getLogger(ProgrammingDemoApi.class);

    private final ProgrammingExerciseCreationUpdateService programmingExerciseCreationUpdateService;

    private final ProgrammingExerciseValidationService programmingExerciseValidationService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ResourceLoaderService resourceLoaderService;

    private final Optional<VersionControlService> versionControlService;

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;

    public ProgrammingDemoApi(ProgrammingExerciseCreationUpdateService programmingExerciseCreationUpdateService,
            ProgrammingExerciseValidationService programmingExerciseValidationService, ProgrammingExerciseRepository programmingExerciseRepository,
            ResourceLoaderService resourceLoaderService, Optional<VersionControlService> versionControlService,
            Optional<ContinuousIntegrationService> continuousIntegrationService) {
        this.programmingExerciseCreationUpdateService = programmingExerciseCreationUpdateService;
        this.programmingExerciseValidationService = programmingExerciseValidationService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.resourceLoaderService = resourceLoaderService;
        this.versionControlService = versionControlService;
        this.continuousIntegrationService = continuousIntegrationService;
    }

    /**
     * Creates the demo programming exercise in the given course if it does not exist yet.
     * <p>
     * This goes through the full production creation path, so the template, solution and test repositories are created and filled with the language template and the initial builds
     * are triggered. That is what makes the exercise participatable: students can clone the repository or use the online editor right away.
     * <p>
     * Creating the repositories and build plans requires a version control and a continuous integration system. When either is missing, i.e. the {@code localvc} or the
     * {@code localci} profile is not active, seeding is skipped instead of failing the startup.
     *
     * @param course the demo course the exercise belongs to.
     * @return the demo exercise, whether it already existed or was created by this call, or empty if seeding it is skipped.
     */
    public Optional<ProgrammingExercise> createDemo(Course course) {
        if (versionControlService.isEmpty() || continuousIntegrationService.isEmpty()) {
            log.info("Skipping the demo programming exercise because no version control or continuous integration system is configured, activate the 'localvc' and 'localci' "
                    + "profiles to seed it");
            return Optional.empty();
        }
        Optional<ProgrammingExercise> existingExercise = programmingExerciseRepository.findByShortNameAndCourseIdWithCompetencies(DEMO_EXERCISE_SHORT_NAME, course.getId());
        if (existingExercise.isPresent()) {
            log.debug("Demo programming exercise already exists, skipping creation");
            return existingExercise;
        }

        ProgrammingExercise programmingExercise = ProgrammingExerciseFactory.generateProgrammingExercise(DEMO_EXERCISE_TITLE, DEMO_EXERCISE_SHORT_NAME,
                readTemplateProblemStatement(), 10.0, 0.0, ExerciseDates.ongoing(), DEMO_PROGRAMMING_LANGUAGE, DEMO_PROJECT_TYPE, DEMO_PACKAGE_NAME, course);
        programmingExercise.getCategories().add("Algorithms");

        // The build configuration is owned by the creation path and only handed in, mirroring ProgrammingExerciseCreationResource#createProgrammingExercise.
        ProgrammingExerciseBuildConfig buildConfig = new ProgrammingExerciseBuildConfig();
        try {
            programmingExerciseValidationService.validateNewProgrammingExerciseSettings(programmingExercise, buildConfig, course);
            ProgrammingExercise createdExercise = programmingExerciseCreationUpdateService.createProgrammingExercise(programmingExercise, buildConfig, false);
            log.info("Created demo programming exercise '{}' with id {}", DEMO_EXERCISE_TITLE, createdExercise.getId());
            return Optional.of(createdExercise);
        }
        catch (GitAPIException | IOException exception) {
            // The orchestrator logs and skips a failing area, so this only has to turn the checked exceptions of the repository setup into a failure of this area.
            throw new IllegalStateException("Could not set up the repositories of the demo programming exercise", exception);
        }
    }

    /**
     * Reads the problem statement the client also offers when an editor creates a programming exercise, so that the demo exercise ships the same assignment as its template
     * repositories. Mirrors {@code FileResource#getTemplateFileContentWithResponse}.
     *
     * @return the template problem statement, or a fallback when the template cannot be read.
     */
    private String readTemplateProblemStatement() {
        String languagePrefix = DEMO_PROGRAMMING_LANGUAGE.name().toLowerCase(Locale.ROOT);
        String projectTypePrefix = DEMO_PROJECT_TYPE.name().toLowerCase(Locale.ROOT);
        try {
            Resource readme = resourceLoaderService.getResource(Path.of("templates", languagePrefix, projectTypePrefix, "readme"));
            if (!readme.exists()) {
                readme = resourceLoaderService.getResource(Path.of("templates", languagePrefix, "readme"));
            }
            if (!readme.exists()) {
                return DEMO_FALLBACK_PROBLEM_STATEMENT;
            }
            try (InputStream inputStream = readme.getInputStream()) {
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        catch (IOException exception) {
            log.warn("Could not read the template problem statement for the demo programming exercise, falling back to a generic one", exception);
            return DEMO_FALLBACK_PROBLEM_STATEMENT;
        }
    }
}
