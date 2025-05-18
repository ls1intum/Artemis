package de.tum.cit.aet.artemis.exercise.service;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.RefSpec;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.StandardCopyOption;

import org.springframework.stereotype.Service;

@Service
public class LocalVCRepositoryInitializer {

    public void createSingleCommitStudentRepo(Path templateBareRepoPath, Path studentBareRepoPath, String defaultBranch) throws Exception {
        // 1. Prepare a working tree by checking out the template bare repo
        Path workingTreeDir = Files.createTempDirectory("template-working-tree");

        try (Git tempGit = Git.cloneRepository()
            .setURI(templateBareRepoPath.toUri().toString())
            .setDirectory(workingTreeDir.toFile())
            .setBare(false)
            .setBranch(defaultBranch)
            .call()) {

            // 2. Prepare a working directory for the new student repo (separate from the bare repo)
            Path studentWorkingDir = Files.createTempDirectory("student-working-copy");

            // 3. Copy the template's working tree files into the student working dir (excluding .git)
            Files.walk(workingTreeDir)
                .filter(path -> !path.toString().contains(".git"))
                .forEach(source -> {
                    try {
                        Path target = studentWorkingDir.resolve(workingTreeDir.relativize(source));
                        if (Files.isDirectory(source)) {
                            Files.createDirectories(target);
                        } else {
                            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });

            // 4. Initialize Git repo in student working dir
            try (Git git = Git.init().setDirectory(studentWorkingDir.toFile()).call()) {
                git.add().addFilepattern(".").call();
                git.commit()
                    .setMessage("Initial import without history")
                    .setAuthor("Artemis", "noreply@artemis.local")
                    .call();

                // 5. Set up and push to the student’s actual bare repo
                git.remoteAdd()
                    .setName("origin")
                    .setUri(new URIish("file://" + studentBareRepoPath.toAbsolutePath()))
                    .call();

                git.push()
                    .setRemote("origin")
                    .setRefSpecs(new RefSpec(defaultBranch + ":" + defaultBranch))
                    .call();
            }
        } finally {
            FileUtils.deleteDirectory(workingTreeDir.toFile());
            // Optional: delete studentWorkingDir too, after verifying push
        }
    }
}
