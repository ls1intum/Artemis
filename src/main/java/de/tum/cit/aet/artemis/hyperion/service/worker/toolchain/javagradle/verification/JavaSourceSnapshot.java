package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import de.tum.cit.aet.artemis.aiworker.api.InteractiveSandbox;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.RepositoryRole;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.workspace.GenerationWorkspace;
import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.workspace.WorkspaceArchive;

/** Server-memory snapshot that restores Java sources removed by source-isolated in-loop verification. */
record JavaSourceSnapshot(Map<String, String> testsFiles, Map<String, String> templateFiles, Map<String, String> solutionFiles, Map<String, String> workspaceJavaSources) {

    static JavaSourceSnapshot capture(InteractiveSandbox sandbox, String sessionId) {
        Map<String, String> tests = readRepository(sandbox, sessionId, RepositoryRole.TESTS);
        Map<String, String> template = readRepository(sandbox, sessionId, RepositoryRole.TEMPLATE);
        Map<String, String> solution = readRepository(sandbox, sessionId, RepositoryRole.SOLUTION);
        Map<String, String> sources = new LinkedHashMap<>();
        addJavaSources(sources, RepositoryRole.TESTS, tests);
        addJavaSources(sources, RepositoryRole.TEMPLATE, template);
        addJavaSources(sources, RepositoryRole.SOLUTION, solution);
        return new JavaSourceSnapshot(tests, template, solution, Map.copyOf(sources));
    }

    void restore(InteractiveSandbox sandbox, String sessionId) {
        if (!workspaceJavaSources.isEmpty()) {
            sandbox.copyIn(sessionId, GenerationWorkspace.WORKSPACE, WorkspaceArchive.buildWorkspaceTarStream(workspaceJavaSources, Map.of()));
        }
    }

    private static Map<String, String> readRepository(InteractiveSandbox sandbox, String sessionId, RepositoryRole repositoryType) {
        String directory = GenerationWorkspace.directoryFor(repositoryType);
        try (TarArchiveInputStream tar = sandbox.copyOut(sessionId, GenerationWorkspace.WORKSPACE + "/" + directory)) {
            if (tar == null) {
                throw new DifferentialVerifier.VerificationInfrastructureException(
                        "The verifier could not snapshot the " + directory + " repository before source-isolated verification", null);
            }
            return WorkspaceArchive.readTar(tar, directory);
        }
        catch (IOException | RuntimeException exception) {
            if (exception instanceof DifferentialVerifier.VerificationInfrastructureException infrastructureException) {
                throw infrastructureException;
            }
            throw new DifferentialVerifier.VerificationInfrastructureException(
                    "The verifier could not snapshot the " + directory + " repository before source-isolated verification", exception);
        }
    }

    private static void addJavaSources(Map<String, String> target, RepositoryRole repositoryType, Map<String, String> files) {
        String directory = GenerationWorkspace.directoryFor(repositoryType);
        files.forEach((path, content) -> {
            if (path.endsWith(".java")) {
                target.put(directory + "/" + path, content);
            }
        });
    }
}
