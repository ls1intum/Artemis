package de.tum.cit.aet.artemis.programming.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_DEMO_AND_SCHEDULING;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.core.api.AbstractApi;
import de.tum.cit.aet.artemis.core.service.ResourceLoaderService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.factories.ExerciseDates;
import de.tum.cit.aet.artemis.exercise.factories.ExerciseFactory;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.localci.service.ci.ContinuousIntegrationService;
import de.tum.cit.aet.artemis.localvc.service.vcs.VersionControlService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
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
    public static final String DEMO_EXERCISE_SHORT_NAME = "demoprog";

    private static final String DEMO_EXERCISE_TITLE = "Sorting Algorithms in Java";

    private static final ProgrammingLanguage DEMO_PROGRAMMING_LANGUAGE = ProgrammingLanguage.JAVA;

    private static final ProjectType DEMO_PROJECT_TYPE = ProjectType.PLAIN_MAVEN;

    private static final String DEMO_PACKAGE_NAME = "de.tum.cit.aet.artemis.demo";

    private static final Logger log = LoggerFactory.getLogger(ProgrammingDemoApi.class);

    private final ProgrammingExerciseCreationUpdateService programmingExerciseCreationUpdateService;

    private final ProgrammingExerciseValidationService programmingExerciseValidationService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ResourceLoaderService resourceLoaderService;

    private final Optional<VersionControlService> versionControlService;

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;

    private final ExerciseVersionService exerciseVersionService;

    public ProgrammingDemoApi(ProgrammingExerciseCreationUpdateService programmingExerciseCreationUpdateService,
            ProgrammingExerciseValidationService programmingExerciseValidationService, ProgrammingExerciseRepository programmingExerciseRepository,
            ResourceLoaderService resourceLoaderService, Optional<VersionControlService> versionControlService, Optional<ContinuousIntegrationService> continuousIntegrationService,
            ExerciseVersionService exerciseVersionService) {
        this.programmingExerciseCreationUpdateService = programmingExerciseCreationUpdateService;
        this.programmingExerciseValidationService = programmingExerciseValidationService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.resourceLoaderService = resourceLoaderService;
        this.versionControlService = versionControlService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.exerciseVersionService = exerciseVersionService;
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

        ProgrammingExercise programmingExercise = ExerciseFactory.populateExercise(new ProgrammingExercise(), DEMO_EXERCISE_TITLE, DEMO_EXERCISE_SHORT_NAME,
                readTemplateProblemStatement(), 10.0, 0.0, ExerciseDates.ongoing(), course);
        programmingExercise.setProgrammingLanguage(DEMO_PROGRAMMING_LANGUAGE);
        programmingExercise.setProjectType(DEMO_PROJECT_TYPE);
        programmingExercise.setPackageName(DEMO_PACKAGE_NAME);
        programmingExercise.setStaticCodeAnalysisEnabled(false);
        programmingExercise.setAssessmentType(AssessmentType.AUTOMATIC);
        // At least one way of working on the exercise has to be allowed, otherwise validateProgrammingSettings rejects the exercise.
        programmingExercise.setAllowOnlineEditor(true);
        programmingExercise.setAllowOfflineIde(true);
        programmingExercise.getCategories().add(ExerciseFactory.exerciseCategory("Algorithms", "#1b97ca"));

        // The build configuration is owned by the creation path and only handed in, mirroring ProgrammingExerciseCreationResource#createProgrammingExercise.
        ProgrammingExerciseBuildConfig buildConfig = new ProgrammingExerciseBuildConfig();
        try {
            programmingExerciseValidationService.validateNewProgrammingExerciseSettings(programmingExercise, buildConfig, course);
            ProgrammingExercise createdExercise = programmingExerciseCreationUpdateService.createProgrammingExercise(programmingExercise, buildConfig, false);
            // Like ProgrammingExerciseCreationResource#createProgrammingExercise, which records the initial version after the setup, not the creation service.
            exerciseVersionService.createExerciseVersion(createdExercise);
            log.info("Created demo programming exercise '{}' with id {}", DEMO_EXERCISE_TITLE, createdExercise.getId());
            return Optional.of(createdExercise);
        }
        catch (GitAPIException | IOException exception) {
            // The orchestrator logs and skips a failing area, so this only has to turn the checked exceptions of the repository setup into a failure of this area.
            throw new IllegalStateException("Could not set up the repositories of the demo programming exercise", exception);
        }
    }

    /**
     * Reads the problem statement the client also offers when an editor creates a programming exercise with the language and project type of the demo exercise, so that the
     * demo exercise ships the same assignment as its template repositories. Mirrors {@code FileResource#getTemplateFileContentWithResponse}.
     *
     * @return the problem statement of the template.
     */
    private String readTemplateProblemStatement() {
        Path readme = Path.of("templates", DEMO_PROGRAMMING_LANGUAGE.name().toLowerCase(Locale.ROOT), DEMO_PROJECT_TYPE.name().toLowerCase(Locale.ROOT), "readme");
        try {
            return resourceLoaderService.getResource(readme).getContentAsString(StandardCharsets.UTF_8);
        }
        catch (IOException exception) {
            throw new UncheckedIOException("Could not read the problem statement template " + readme, exception);
        }
    }
}
