package de.tum.cit.aet.artemis.buildagent.service.runner;

import java.nio.file.Path;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Repositories cloned by the build agent and ready to be copied into an isolated build environment.
 * Git credentials never need to be exposed to the build workload.
 */
public record PreparedBuildJob(Path assignmentRepository, Path testRepository, @Nullable Path solutionRepository, List<Path> auxiliaryRepositories) {
}
