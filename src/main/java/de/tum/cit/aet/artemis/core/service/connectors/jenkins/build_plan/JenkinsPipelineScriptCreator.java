package de.tum.cit.aet.artemis.core.service.connectors.jenkins.build_plan;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.io.IOUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.core.config.ProgrammingLanguageConfiguration;
import de.tum.cit.aet.artemis.core.exception.JenkinsException;
import de.tum.cit.aet.artemis.core.service.connectors.ci.AbstractBuildPlanCreator;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.repository.BuildPlanRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.service.ResourceLoaderService;

@Profile(PROFILE_CORE)
@Component
public class JenkinsPipelineScriptCreator extends AbstractBuildPlanCreator {

    private static final String REPLACE_DOCKER_IMAGE_NAME = "#dockerImage";

    private static final String REPLACE_DOCKER_ARGS = "#dockerArgs";

    private static final String REPLACE_IS_STATIC_CODE_ANALYSIS_ENABLED = "#isStaticCodeAnalysisEnabled";

    private static final String REPLACE_TESTWISE_COVERAGE = "#testWiseCoverage";

    private final ResourceLoaderService resourceLoaderService;

    private final ProgrammingLanguageConfiguration programmingLanguageConfiguration;

    public JenkinsPipelineScriptCreator(final BuildPlanRepository buildPlanRepository, final ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository,
            final ResourceLoaderService resourceLoaderService, final ProgrammingLanguageConfiguration programmingLanguageConfiguration) {
        super(buildPlanRepository, programmingExerciseBuildConfigRepository);

        this.resourceLoaderService = resourceLoaderService;
        this.programmingLanguageConfiguration = programmingLanguageConfiguration;
    }

    @Override
    protected String generateDefaultBuildPlan(final ProgrammingExercise exercise) {
        final ProgrammingLanguage programmingLanguage = exercise.getProgrammingLanguage();
        final Optional<ProjectType> projectType = Optional.ofNullable(exercise.getProjectType());

        final String pipelineScript = loadPipelineScript(exercise, projectType);

        final boolean isStaticCodeAnalysisEnabled = exercise.isStaticCodeAnalysisEnabled();
        final boolean isTestwiseCoverageAnalysisEnabled = exercise.getBuildConfig().isTestwiseCoverageEnabled();
        final var replacements = getReplacements(programmingLanguage, projectType, isStaticCodeAnalysisEnabled, isTestwiseCoverageAnalysisEnabled);

        return replaceVariablesInBuildPlanTemplate(replacements, pipelineScript);
    }

    /**
     * Loads the template for the {@code pipeline.groovy} script.
     *
     * @param exercise    The exercise for which a pipeline should be loaded.
     * @param projectType The project type of the exercise.
     * @return The template for a {@code pipeline.groovy} script.
     */
    private String loadPipelineScript(final ProgrammingExercise exercise, final Optional<ProjectType> projectType) {
        final ProgrammingLanguage programmingLanguage = exercise.getProgrammingLanguage();
        final boolean isSequentialTestRuns = exercise.getBuildConfig().hasSequentialTestRuns();

        final Path pipelinePath = buildResourcePath(programmingLanguage, projectType, isSequentialTestRuns);
        final Resource resource = resourceLoaderService.getResource(pipelinePath);

        try (InputStream inputStream = resource.getInputStream()) {
            return IOUtils.toString(inputStream, Charset.defaultCharset());
        }
        catch (IOException e) {
            throw new JenkinsException("Could not load pipeline script definition.", e);
        }
    }

    private Map<String, String> getReplacements(final ProgrammingLanguage programmingLanguage, final Optional<ProjectType> projectType, final boolean isStaticCodeAnalysisEnabled,
            final boolean isTestwiseCoverageAnalysisEnabled) {
        final Map<String, String> replacements = new HashMap<>();

        replacements.put(REPLACE_IS_STATIC_CODE_ANALYSIS_ENABLED, String.valueOf(isStaticCodeAnalysisEnabled));
        replacements.put(REPLACE_TESTWISE_COVERAGE, String.valueOf(isTestwiseCoverageAnalysisEnabled));
        replacements.put(REPLACE_DOCKER_IMAGE_NAME, programmingLanguageConfiguration.getImage(programmingLanguage, projectType));
        replacements.put(REPLACE_DOCKER_ARGS, String.join(" ", programmingLanguageConfiguration.getDefaultDockerFlags()));

        return replacements;
    }

    private Path buildResourcePath(final ProgrammingLanguage programmingLanguage, final Optional<ProjectType> projectType, final boolean isSequentialRuns) {
        if (programmingLanguage == null) {
            throw new IllegalArgumentException("ProgrammingLanguage should not be null");
        }

        final var pipelineScriptFilename = "pipeline.groovy";
        final var regularOrSequentialDir = isSequentialRuns ? "sequentialRuns" : "regularRuns";
        final var programmingLanguageName = programmingLanguage.name().toLowerCase();
        final Optional<String> projectTypeName = getProjectTypeName(programmingLanguage, projectType);

        Path resourcePath = Path.of("templates", "jenkins", programmingLanguageName);
        if (projectTypeName.isPresent()) {
            resourcePath = resourcePath.resolve(projectTypeName.get());
        }

        return resourcePath.resolve(regularOrSequentialDir).resolve(pipelineScriptFilename);
    }

    private Optional<String> getProjectTypeName(final ProgrammingLanguage programmingLanguage, final Optional<ProjectType> projectType) {
        // Set a project type name in case the chosen Jenkinsfile also depend on the project type
        if (projectType.isPresent() && ProgrammingLanguage.C.equals(programmingLanguage)) {
            return Optional.of(projectType.get().name().toLowerCase(Locale.ROOT));
        }
        else if (projectType.isPresent() && projectType.get().isGradle()) {
            return Optional.of("gradle");
        }
        else if (projectType.isPresent() && projectType.get().equals(ProjectType.MAVEN_BLACKBOX)) {
            return Optional.of("blackbox");
        }
        // Maven is also the project type for all other Java exercises (also if the project type is not present)
        else if (ProgrammingLanguage.JAVA.equals(programmingLanguage)) {
            return Optional.of("maven");
        }
        else {
            return Optional.empty();
        }
    }
}
