package de.tum.in.www1.artemis.service.connectors.gitlabci;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.service.ResourceLoaderService;
import de.tum.in.www1.artemis.service.connectors.jenkins.JenkinsBuildPlanCreator;

@Service
@Profile("gitlabci")
public class GitLabCIBuildPlanService {

    private static final Logger LOG = LoggerFactory.getLogger(JenkinsBuildPlanCreator.class);

    private static final String FILE_NAME = ".gitlab-ci.yml";

    private final ResourceLoaderService resourceLoaderService;

    public GitLabCIBuildPlanService(ResourceLoaderService resourceLoaderService) {
        this.resourceLoaderService = resourceLoaderService;
    }

    public String getBuildPlan(ProgrammingExercise programmingExercise) {
        Optional<String> projectTypeName;
        if (programmingExercise.getProgrammingLanguage().equals(ProgrammingLanguage.JAVA)) {
            projectTypeName = Optional.of("maven");
        }
        else {
            projectTypeName = Optional.empty();
        }
        String[] resourcePath = projectTypeName
                .map(name -> new String[] { "templates", "gitlabci", programmingExercise.getProgrammingLanguage().name().toLowerCase(), name,
                        programmingExercise.hasSequentialTestRuns() ? "sequentialRuns" : "regularRuns", FILE_NAME })
                .orElseGet(() -> new String[] { "templates", "gitlabci", programmingExercise.getProgrammingLanguage().name().toLowerCase(),
                        programmingExercise.hasSequentialTestRuns() ? "sequentialRuns" : "regularRuns", FILE_NAME });

        final Resource resource = resourceLoaderService.getResource(resourcePath);
        try {
            return StreamUtils.copyToString(resource.getInputStream(), Charset.defaultCharset());
        }
        catch (IOException e) {
            final var errorMessage = "Error loading template GitLab CI build configuration " + e.getMessage();
            LOG.error(errorMessage, e);
            throw new IllegalStateException(errorMessage, e);
        }

    }
}
