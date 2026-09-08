package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import de.tum.cit.aet.artemis.buildagent.service.InteractiveSandbox;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace.GenerationWorkspaceService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.workspace.WorkspaceArchive;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/** Server-memory snapshot that restores Java sources removed by source-isolated in-loop verification. */
record JavaSourceSnapshot(Map<String, String> testsFiles, Map<String, String> templateFiles, Map<String, String> solutionFiles, Map<String, String> workspaceJavaSources) {

    static JavaSourceSnapshot capture(InteractiveSandbox sandbox, String sessionId) {
        Map<String, String> tests = readRepository(sandbox, sessionId, RepositoryType.TESTS);
        Map<String, String> template = readRepository(sandbox, sessionId, RepositoryType.TEMPLATE);
        Map<String, String> solution = readRepository(sandbox, sessionId, RepositoryType.SOLUTION);
        Map<String, String> sources = new LinkedHashMap<>();
        addJavaSources(sources, RepositoryType.TESTS, tests);
        addJavaSources(sources, RepositoryType.TEMPLATE, template);
        addJavaSources(sources, RepositoryType.SOLUTION, solution);
        return new JavaSourceSnapshot(tests, template, solution, Map.copyOf(sources));
    }

    void restore(InteractiveSandbox sandbox, String sessionId) {
        if (!workspaceJavaSources.isEmpty()) {
            sandbox.copyIn(sessionId, GenerationWorkspaceService.WORKSPACE, WorkspaceArchive.buildWorkspaceTarStream(workspaceJavaSources, Map.of()));
        }
    }

    private static Map<String, String> readRepository(InteractiveSandbox sandbox, String sessionId, RepositoryType repositoryType) {
        String directory = GenerationWorkspaceService.directoryFor(repositoryType);
        try (TarArchiveInputStream tar = sandbox.copyOut(sessionId, GenerationWorkspaceService.WORKSPACE + "/" + directory)) {
            if (tar == null) {
                throw new DifferentialVerificationService.VerificationInfrastructureException(
                        "The verifier could not snapshot the " + directory + " repository before source-isolated verification", null);
            }
            return WorkspaceArchive.readTar(tar, directory);
        }
        catch (IOException | RuntimeException exception) {
            if (exception instanceof DifferentialVerificationService.VerificationInfrastructureException infrastructureException) {
                throw infrastructureException;
            }
            throw new DifferentialVerificationService.VerificationInfrastructureException(
                    "The verifier could not snapshot the " + directory + " repository before source-isolated verification", exception);
        }
    }

    private static void addJavaSources(Map<String, String> target, RepositoryType repositoryType, Map<String, String> files) {
        String directory = GenerationWorkspaceService.directoryFor(repositoryType);
        files.forEach((path, content) -> {
            if (path.endsWith(".java")) {
                target.put(directory + "/" + path, content);
            }
        });
    }
}
