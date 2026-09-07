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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(KubernetesBuildArchiveService.class);

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
                    addDirectory(tar, preparedBuildJob.testRepository(), targetDirectory(testCheckoutPath));
                    addDirectory(tar, preparedBuildJob.assignmentRepository(), targetDirectory(assignmentCheckoutPath));

                    if (preparedBuildJob.solutionRepository() != null) {
                        String solutionCheckoutPath = checkoutPath(buildJob.buildConfig().solutionCheckoutPath(),
                                RepositoryCheckoutPath.SOLUTION.forProgrammingLanguage(buildJob.buildConfig().programmingLanguage()));
                        addDirectory(tar, preparedBuildJob.solutionRepository(), targetDirectory(solutionCheckoutPath));
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
        Path normalizedSourceRoot = sourceRoot.toAbsolutePath().normalize();
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
                Path linkTarget = Files.readSymbolicLink(path);
                Path parent = path.toAbsolutePath().getParent();
                Path resolvedTarget = (parent == null ? linkTarget.toAbsolutePath() : parent.resolve(linkTarget)).normalize();
                // A repository may contain a symbolic link that escapes its own root. Archiving it would let the extraction in the build container write outside the
                // workspace, so such links are dropped instead of being carried into the archive.
                if (!resolvedTarget.startsWith(normalizedSourceRoot)) {
                    log.warn("Skipping symbolic link {} because its target {} points outside the repository", path, linkTarget);
                    return;
                }
                TarArchiveEntry entry = new TarArchiveEntry(entryName, TarConstants.LF_SYMLINK);
                entry.setLinkName(linkTarget.toString().replace(path.getFileSystem().getSeparator(), "/"));
                entry.setMode(0777);
                tar.putArchiveEntry(entry);
                tar.closeArchiveEntry();
            }
        });
    }

    /**
     * Resolves the checkout path of a repository, falling back to the language default when the exercise does not customise it.
     * <p>
     * The language default is intentionally empty for several languages (for example the test repository of a Java exercise), which means that the repository is checked out
     * into the working directory itself rather than into a subdirectory.
     *
     * @param configuredPath the checkout path configured on the exercise, may be blank
     * @param defaultPath    the language default, may be empty
     * @return the validated relative checkout path, or an empty string for the working directory itself
     */
    private String checkoutPath(String configuredPath, String defaultPath) {
        String path = StringUtils.isBlank(configuredPath) ? defaultPath : configuredPath;
        return StringUtils.isBlank(path) ? "" : validateRelativePath(path);
    }

    private static String targetDirectory(String checkoutPath) {
        return checkoutPath.isEmpty() ? TESTING_DIRECTORY : TESTING_DIRECTORY + "/" + checkoutPath;
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
