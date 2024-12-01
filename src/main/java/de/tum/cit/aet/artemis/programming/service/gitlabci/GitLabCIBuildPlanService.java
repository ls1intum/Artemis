package de.tum.cit.aet.artemis.programming.service.gitlabci;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import de.tum.cit.aet.artemis.core.service.ResourceLoaderService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.repository.BuildPlanRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.service.ci.AbstractBuildPlanCreator;

// Gitlab support will be removed in 8.0.0. Please migrate to LocalVC using e.g. the PR https://github.com/ls1intum/Artemis/pull/8972
@Deprecated(since = "7.5.0", forRemoval = true)

@Service
@Profile("gitlabci")
public class GitLabCIBuildPlanService extends AbstractBuildPlanCreator {

    private static final Logger log = LoggerFactory.getLogger(GitLabCIBuildPlanService.class);

    private static final String FILE_NAME = ".gitlab-ci.yml";

    private final ResourceLoaderService resourceLoaderService;

    public GitLabCIBuildPlanService(BuildPlanRepository buildPlanRepository, ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository,
            ResourceLoaderService resourceLoaderService) {
        super(buildPlanRepository, programmingExerciseBuildConfigRepository);

        this.resourceLoaderService = resourceLoaderService;
    }

    /**
     * Generate the default build plan for the project type of the given programming exercise.
     *
     * @param programmingExercise the programming exercise for which to get the build plan
     * @return the default build plan
     */
    @Override
    public String generateDefaultBuildPlan(ProgrammingExercise programmingExercise) {
        final Optional<String> projectTypeName = getProjectTypeName(programmingExercise);
        final Path resourcePath = buildResourcePath(programmingExercise.getProgrammingLanguage(), projectTypeName, programmingExercise.getBuildConfig().hasSequentialTestRuns());
        final Resource resource = resourceLoaderService.getResource(resourcePath);

        try {
            return StreamUtils.copyToString(resource.getInputStream(), Charset.defaultCharset());
        }
        catch (IOException ex) {
            log.error("Error loading template GitLab CI build configuration", ex);
            throw new IllegalStateException("Error loading template GitLab CI build configuration", ex);
        }
    }

    private static Optional<String> getProjectTypeName(final ProgrammingExercise programmingExercise) {
        if (ProgrammingLanguage.JAVA.equals(programmingExercise.getProgrammingLanguage())) {
            return Optional.of("maven");
        }
        else {
            return Optional.empty();
        }
    }

    private static Path buildResourcePath(final ProgrammingLanguage programmingLanguage, final Optional<String> projectTypeName, final boolean sequentialTestRuns) {
        final String programmingLanguageName = programmingLanguage.name().toLowerCase();
        final String regularOrSequentialDir = sequentialTestRuns ? "sequentialRuns" : "regularRuns";

        Path resourcePath = Path.of("templates", "gitlabci", programmingLanguageName);
        if (projectTypeName.isPresent()) {
            resourcePath = resourcePath.resolve(projectTypeName.get());
        }

        return resourcePath.resolve(regularOrSequentialDir).resolve(FILE_NAME);
    }
}
