package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.programming.domain.Repository;

@Profile(PROFILE_CORE)
@Lazy
@Component
public class GitArchiveHelper {

    private static final Logger log = LoggerFactory.getLogger(GitArchiveHelper.class);

    private static final Set<String> IGNORED_ZIP_FILE_NAMES = Set.of("gc.log.lock");

    InputStreamResource exportRepositoryWithFullHistoryToMemory(Repository repository, String filename) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream(); ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {

            // Add the .git directory (full history)
            Path bareRepoPath = repository.getDirectory().toPath();
            addDirectoryToZip(zipOutputStream, bareRepoPath, bareRepoPath);

            // Add the working tree snapshot using ArchiveCommand for HEAD
            try {
                byte[] archiveData = createJGitArchive(repository);
                if (archiveData.length > 0) {
                    try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(archiveData))) {
                        ZipEntry entry;
                        while ((entry = zipInputStream.getNextEntry()) != null) {
                            zipOutputStream.putNextEntry(new ZipEntry(entry.getName()));
                            zipInputStream.transferTo(zipOutputStream);
                            zipOutputStream.closeEntry();
                        }
                    }
                }
            }
            catch (Exception e) {
                log.debug("Could not create archive for HEAD: {}", e.getMessage());
            }

            zipOutputStream.finish();
            byte[] zipData = outputStream.toByteArray();
            return createInputStreamResource(zipData, filename);
        }
    }

    @SuppressWarnings("resource")
    byte[] createJGitArchive(Repository repository) throws GitAPIException, IOException {
        ObjectId treeId = repository.resolve("HEAD");
        if (treeId == null) {
            log.debug("Could not resolve tree for HEAD");
            return new byte[0];
        }

        ByteArrayOutputStream archiveData = new ByteArrayOutputStream();
        try (Git git = new Git(repository)) {
            git.archive().setFormat("zip").setTree(treeId).setOutputStream(archiveData).call();
        }
        return archiveData.toByteArray();
    }

    private InputStreamResource createInputStreamResource(byte[] zipData, String filename) {
        return new InputStreamResource(new ByteArrayInputStream(zipData)) {

            @Override
            public String getFilename() {
                return filename + ".zip";
            }

            @Override
            public long contentLength() {
                return zipData.length;
            }
        };
    }

    void addDirectoryToZip(ZipOutputStream zipOutputStream, Path rootPath, Path pathToAdd) throws IOException {
        try (var paths = Files.walk(pathToAdd)) {
            paths.forEach(path -> {
                try {
                    String relativePath = rootPath.relativize(path).toString().replace("\\", "/");
                    String zipEntryName = ".git" + "/" + relativePath;

                    // Skip ignored files like ephemeral lock files
                    String fileName = path.getFileName().toString();
                    if (IGNORED_ZIP_FILE_NAMES.contains(fileName)) {
                        return;
                    }

                    if (Files.isDirectory(path)) {
                        if (!zipEntryName.endsWith("/")) {
                            zipEntryName += "/";
                        }
                        zipOutputStream.putNextEntry(new ZipEntry(zipEntryName));
                    }
                    else if (Files.isRegularFile(path)) {
                        zipOutputStream.putNextEntry(new ZipEntry(zipEntryName));
                        FileUtils.copyFile(path.toFile(), zipOutputStream);
                    }
                    zipOutputStream.closeEntry();
                }
                catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }
}
