package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

class GitServiceTest {

    @TempDir
    private Path tempDirectory;

    @Test
    void getOrCheckoutRepositoryChecksOutRequestedBranch() throws Exception {
        Path remoteRoot = tempDirectory.resolve("remotes");
        Path bareRepositoryPath = remoteRoot.resolve("TEST").resolve("test-template.git");
        Files.createDirectories(bareRepositoryPath.getParent());

        try (Git ignored = Git.init().setBare(true).setInitialBranch("main").setDirectory(bareRepositoryPath.toFile()).call();
                Git source = Git.init().setInitialBranch("main").setDirectory(tempDirectory.resolve("source").toFile()).call()) {
            Path marker = source.getRepository().getWorkTree().toPath().resolve("branch.txt");
            FileUtils.writeStringToFile(marker.toFile(), "main", StandardCharsets.UTF_8);
            source.add().addFilepattern("branch.txt").call();
            source.commit().setMessage("Add main marker").setAuthor("Artemis", "artemis@example.com").setSign(false).call();
            source.push().setRemote(bareRepositoryPath.toUri().toString()).setRefSpecs(new RefSpec("refs/heads/main:refs/heads/main")).call();

            source.checkout().setCreateBranch(true).setName("release").call();
            FileUtils.writeStringToFile(marker.toFile(), "release", StandardCharsets.UTF_8);
            source.add().addFilepattern("branch.txt").call();
            source.commit().setMessage("Add release marker").setAuthor("Artemis", "artemis@example.com").setSign(false).call();
            source.push().setRemote(bareRepositoryPath.toUri().toString()).setRefSpecs(new RefSpec("refs/heads/release:refs/heads/release")).call();
        }

        GitService service = new GitService();
        ReflectionTestUtils.setField(service, "localVCBasePath", remoteRoot);
        LocalVCRepositoryUri repositoryUri = new LocalVCRepositoryUri(URI.create("http://localhost"), "TEST", "test-template");

        try (var repository = service.getOrCheckoutRepository(repositoryUri, repositoryUri, tempDirectory.resolve("checkout"), false, "release", false)) {
            assertThat(repository.getBranch()).isEqualTo("release");
            assertThat(Files.readString(repository.getLocalPath().resolve("branch.txt"))).isEqualTo("release");
        }
    }
}
