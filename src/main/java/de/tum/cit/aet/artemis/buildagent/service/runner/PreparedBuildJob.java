package de.tum.cit.aet.artemis.buildagent.service.runner;

import java.nio.file.Path;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Repositories cloned by the build agent and ready to be copied into an isolated build environment.
 * Git credentials never need to be exposed to the build workload.
 * <p>
 * The test repository is null for a container of a multi-container build plan that is scoped to run without it (see
 * the repository scoping in {@code LocalCITriggerService}); such a container never receives the instructor's tests.
 */
public record PreparedBuildJob(Path assignmentRepository, @Nullable Path testRepository, @Nullable Path solutionRepository, List<Path> auxiliaryRepositories) {
}
