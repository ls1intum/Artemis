package de.tum.in.www1.artemis.service.connectors.gitlabci;

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

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.repository.BuildPlanRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.service.ResourceLoaderService;
import de.tum.in.www1.artemis.service.connectors.ci.AbstractBuildPlanCreator;

@Service
@Profile("gitlabci")
public class GitLabCIBuildPlanService extends AbstractBuildPlanCreator {

    private static final Logger log = LoggerFactory.getLogger(GitLabCIBuildPlanService.class);

    private static final String FILE_NAME = ".gitlab-ci.yml";

    private final ResourceLoaderService resourceLoaderService;

    public GitLabCIBuildPlanService(BuildPlanRepository buildPlanRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            ResourceLoaderService resourceLoaderService) {
        super(buildPlanRepository, programmingExerciseRepository);

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
        final Path resourcePath = buildResourcePath(programmingExercise.getProgrammingLanguage(), projectTypeName, programmingExercise.hasSequentialTestRuns());
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
