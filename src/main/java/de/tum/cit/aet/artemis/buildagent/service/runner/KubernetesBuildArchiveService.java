package de.tum.cit.aet.artemis.buildagent.service.runner;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.core.service.TempFileUtilService;
import de.tum.cit.aet.artemis.localci.exception.LocalCIException;
import de.tum.cit.aet.artemis.programming.service.RepositoryCheckoutService.RepositoryCheckoutPath;

/**
 * Creates the runner-neutral input archive consumed by the trusted Kubernetes helper container.
 */
@Service
@Lazy
@Profile(PROFILE_BUILDAGENT)
@ConditionalOnProperty(prefix = "artemis.continuous-integration", name = "build-runner", havingValue = "kubernetes")
public class KubernetesBuildArchiveService {

    private static final String TESTING_DIRECTORY = "testing-dir";

    private final TempFileUtilService tempFileUtilService;

    public KubernetesBuildArchiveService(TempFileUtilService tempFileUtilService) {
        this.tempFileUtilService = tempFileUtilService;
    }

    /**
     * Creates a temporary tar archive whose paths are relative to the shared {@code /var/tmp} volume.
     *
     * @param buildJob         build configuration and checkout paths
     * @param preparedBuildJob repositories cloned on the agent
     * @return path of the temporary tar archive; the caller owns and must delete it
     */
    public Path createInputArchive(BuildJobQueueItem buildJob, PreparedBuildJob preparedBuildJob) {
        try {
            Path archive = tempFileUtilService.createTempFile("artemis-localci-" + safeFileName(buildJob.id()) + "-", ".tar");
            try {
                try (OutputStream output = Files.newOutputStream(archive); TarArchiveOutputStream tar = new TarArchiveOutputStream(output)) {
                    tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
                    tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);

                    String assignmentCheckoutPath = checkoutPath(buildJob.buildConfig().assignmentCheckoutPath(),
                            RepositoryCheckoutPath.ASSIGNMENT.forProgrammingLanguage(buildJob.buildConfig().programmingLanguage()));
                    String testCheckoutPath = checkoutPath(buildJob.buildConfig().testCheckoutPath(),
                            RepositoryCheckoutPath.TEST.forProgrammingLanguage(buildJob.buildConfig().programmingLanguage()));
                    addDirectory(tar, preparedBuildJob.testRepository(), TESTING_DIRECTORY + "/" + testCheckoutPath);
                    addDirectory(tar, preparedBuildJob.assignmentRepository(), TESTING_DIRECTORY + "/" + assignmentCheckoutPath);

                    if (preparedBuildJob.solutionRepository() != null) {
                        String solutionCheckoutPath = checkoutPath(buildJob.buildConfig().solutionCheckoutPath(),
                                RepositoryCheckoutPath.SOLUTION.forProgrammingLanguage(buildJob.buildConfig().programmingLanguage()));
                        addDirectory(tar, preparedBuildJob.solutionRepository(), TESTING_DIRECTORY + "/" + solutionCheckoutPath);
                    }

                    List<Path> auxiliaryRepositories = preparedBuildJob.auxiliaryRepositories();
                    String[] auxiliaryCheckoutDirectories = buildJob.repositoryInfo().auxiliaryRepositoryCheckoutDirectories();
                    if (auxiliaryRepositories.size() != auxiliaryCheckoutDirectories.length) {
                        throw new LocalCIException("The number of auxiliary repositories does not match the number of checkout directories");
                    }
                    for (int i = 0; i < auxiliaryRepositories.size(); i++) {
                        addDirectory(tar, auxiliaryRepositories.get(i), TESTING_DIRECTORY + "/" + validateRelativePath(auxiliaryCheckoutDirectories[i]));
                    }

                    byte[] script = buildJob.buildConfig().buildScript().getBytes(StandardCharsets.UTF_8);
                    TarArchiveEntry scriptEntry = new TarArchiveEntry("script.sh");
                    scriptEntry.setSize(script.length);
                    scriptEntry.setMode(0777);
                    tar.putArchiveEntry(scriptEntry);
                    tar.write(script);
                    tar.closeArchiveEntry();
                    tar.finish();
                }
                return archive;
            }
            catch (IOException | RuntimeException e) {
                deleteFailedArchive(archive, e);
                throw e;
            }
        }
        catch (IOException e) {
            throw new LocalCIException("Could not create the Kubernetes build input archive", e);
        }
    }

    private void deleteFailedArchive(Path archive, Exception originalFailure) {
        try {
            Files.deleteIfExists(archive);
        }
        catch (IOException cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
        }
    }

    private void addDirectory(TarArchiveOutputStream tar, Path sourceRoot, String targetRoot) throws IOException {
        String normalizedTarget = validateRelativePath(targetRoot);
        Files.walkFileTree(sourceRoot, new SimpleFileVisitor<>() {

            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                addEntry(directory, attributes, true);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                addEntry(file, attributes, false);
                return FileVisitResult.CONTINUE;
            }

            private void addEntry(Path path, BasicFileAttributes attributes, boolean directory) throws IOException {
                Path relative = sourceRoot.relativize(path);
                String relativeName = relative.toString().replace(path.getFileSystem().getSeparator(), "/");
                String entryName = relativeName.isEmpty() ? normalizedTarget : normalizedTarget + "/" + relativeName;
                if (directory && !entryName.endsWith("/")) {
                    entryName += "/";
                }

                if (attributes.isSymbolicLink()) {
                    addSymbolicLink(path, entryName);
                    return;
                }

                TarArchiveEntry entry = new TarArchiveEntry(path.toFile(), entryName);
                entry.setMode(directory ? 0777 : (Files.isExecutable(path) ? 0777 : 0666));
                if (!directory) {
                    entry.setSize(attributes.size());
                }
                tar.putArchiveEntry(entry);
                if (!directory) {
                    FileUtils.copyFile(path.toFile(), tar);
                }
                tar.closeArchiveEntry();
            }

            private void addSymbolicLink(Path path, String entryName) throws IOException {
                TarArchiveEntry entry = new TarArchiveEntry(entryName, TarConstants.LF_SYMLINK);
                entry.setLinkName(Files.readSymbolicLink(path).toString().replace(path.getFileSystem().getSeparator(), "/"));
                entry.setMode(0777);
                tar.putArchiveEntry(entry);
                tar.closeArchiveEntry();
            }
        });
    }

    private String checkoutPath(String configuredPath, String defaultPath) {
        return validateRelativePath(StringUtils.isBlank(configuredPath) ? defaultPath : configuredPath);
    }

    static String validateRelativePath(String path) {
        if (path == null || path.isBlank() || path.startsWith("/") || path.contains("..") || !path.matches("[a-zA-Z0-9_./-]+")) {
            throw new LocalCIException("Invalid checkout path for Kubernetes build execution: " + path);
        }
        return path.replaceAll("^\\./", "").replaceAll("/$", "");
    }

    private String safeFileName(String value) {
        return value.replaceAll("[^a-zA-Z0-9_.-]", "-");
    }
}
