package de.tum.cit.aet.artemis.programming.service;

import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;

@Service
public class TemplateRepositoryService {

    public void createSquashedStudentRepo(VcsRepositoryUri templateUri, VcsRepositoryUri studentUri) throws Exception {
        // 1) Clone template into a temp dir
        String templateUrl = templateUri.toString();
        String studentUrl = studentUri.toString();

        Path tmpDir = Files.createTempDirectory("template-");

        try (Git git = Git.cloneRepository().setURI(templateUrl).setDirectory(tmpDir.toFile()).call()) {
            // 2) Find the very first commit (root)
            ObjectId head = git.getRepository().resolve("refs/heads/master");
            RevWalk revWalk = new RevWalk(git.getRepository());
            RevCommit root = revWalk.parseCommit(head);
            while (root.getParentCount() > 0) {
                root = revWalk.parseCommit(root.getParent(0));
            }
            revWalk.close();

            // 3) Soft‐reset to that root commit: bring working tree to root’s contents, but keep all changes staged
            git.reset().setMode(ResetType.SOFT).setRef(root.name()).call();

            // 4) Commit everything as one new “Initial template” commit
            git.commit().setMessage("Initial template commit").setAuthor("Artemis", "noreply@artemis.example.com").call();

            // 5) Point a new remote at the student’s repo and push
            git.remoteAdd().setName("student").setUri(new URIish(studentUrl)).call();

            git.push().setRemote("student").setRefSpecs(new RefSpec("master:master")).setForce(true)   // overwrite if existing history
                    .call();
        }
        // optionally delete tmpDir recursively
        // FileUtils.deleteDirectory(tmpDir.toFile());
    }
}
