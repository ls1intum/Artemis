package de.tum.cit.aet.artemis.programming.service.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.security.SecurityFrameworkState;

/**
 * Reads the files of a commit of an exercise-tests repository for the {@link SecurityFrameworkService}: the state of the Security Framework, the pinned Ares
 * version and the policy files, also from earlier commits. Unlike a checkout, it never follows symbolic links.
 */
final class SecurityFrameworkCommitFiles {

    private static final Logger log = LoggerFactory.getLogger(SecurityFrameworkCommitFiles.class);

    private static final String GRADLE_BUILD_FILE = "build.gradle";

    private static final String MAVEN_BUILD_FILE = "pom.xml";

    private SecurityFrameworkCommitFiles() {
    }

    /**
     * @return the Ares version the build file of the commit pins; empty if it pins none
     */
    static Optional<String> aresVersion(Repository repository, RevTree tree) throws IOException {
        String gradleBuild = readFile(repository, tree, GRADLE_BUILD_FILE).orElse(null);
        String mavenPom = readFile(repository, tree, MAVEN_BUILD_FILE).orElse(null);
        return SecurityPolicyRepositoryFiles.parseAresVersion(gradleBuild, mavenPom);
    }

    /**
     * @return the path of the build file that pins the Ares version in the commit, e.g. {@code build.gradle}; empty if none pins one
     */
    static Optional<String> aresBuildFile(Repository repository, RevTree tree) throws IOException {
        return SecurityPolicyRepositoryFiles.detectBuildTool(readFile(repository, tree, GRADLE_BUILD_FILE).orElse(null), readFile(repository, tree, MAVEN_BUILD_FILE).orElse(null))
                .map(buildTool -> buildTool == SecurityPolicyRepositoryFiles.BuildTool.GRADLE ? GRADLE_BUILD_FILE : MAVEN_BUILD_FILE);
    }

    /**
     * Applies {@link SecurityPolicyRepositoryFiles#evaluate(Map, SecurityPolicyRepositoryFiles.PolicyFileReader, SecurityPolicyRepositoryFiles.BuildFiles)}
     * to a commit of the repository. Like the scan of a checkout, it only considers regular files, never symbolic links, and selects the Java sources with
     * {@link SecurityPolicyRepositoryFiles#isJavaSourcePath}, so build output is skipped in the same way.
     */
    static SecurityFrameworkState evaluate(Repository repository, RevTree tree) throws IOException {
        SecurityPolicyRepositoryFiles.BuildFiles buildFiles = new SecurityPolicyRepositoryFiles.BuildFiles(readFile(repository, tree, GRADLE_BUILD_FILE).orElse(null),
                readFile(repository, tree, MAVEN_BUILD_FILE).orElse(null));
        return SecurityPolicyRepositoryFiles.evaluate(javaSources(repository, tree), path -> readPolicyFile(repository, tree, path), buildFiles);
    }

    static Map<String, String> javaSources(Repository repository, RevTree tree) throws IOException {
        Map<String, ObjectId> files = new TreeMap<>();
        try (TreeWalk treeWalk = new TreeWalk(repository)) {
            treeWalk.addTree(tree);
            treeWalk.setRecursive(true);
            while (treeWalk.next()) {
                // TYPE_FILE covers regular and executable files, but not symbolic links, whose type is TYPE_SYMLINK.
                if ((treeWalk.getRawMode(0) & FileMode.TYPE_MASK) == FileMode.TYPE_FILE) {
                    files.put(treeWalk.getPathString(), treeWalk.getObjectId(0));
                }
            }
        }
        Map<String, String> javaSources = new TreeMap<>();
        for (Map.Entry<String, ObjectId> file : files.entrySet()) {
            if (SecurityPolicyRepositoryFiles.isJavaSourcePath(file.getKey(), files::containsKey)) {
                javaSources.put(file.getKey(), new String(repository.open(file.getValue()).getBytes(), StandardCharsets.UTF_8));
            }
        }
        return javaSources;
    }

    /**
     * Reads a policy file of a commit: only a regular file is read, and only if it is not larger than {@link SecurityPolicyYaml#MAX_POLICY_SIZE}, which is
     * checked before its content is loaded.
     */
    static SecurityPolicyRepositoryFiles.PolicyFileContent readPolicyFile(Repository repository, RevTree tree, String path) {
        try (TreeWalk treeWalk = TreeWalk.forPath(repository, path, tree)) {
            if (treeWalk == null) {
                return new SecurityPolicyRepositoryFiles.PolicyFileContent(
                        hasNonDirectoryParent(repository, tree, path) ? SecurityFrameworkState.PolicyFileProblem.INVALID_PATH : SecurityFrameworkState.PolicyFileProblem.MISSING,
                        null);
            }
            if ((treeWalk.getRawMode(0) & FileMode.TYPE_MASK) != FileMode.TYPE_FILE) {
                return new SecurityPolicyRepositoryFiles.PolicyFileContent(SecurityFrameworkState.PolicyFileProblem.INVALID_PATH, null);
            }
            ObjectLoader loader = repository.open(treeWalk.getObjectId(0));
            if (loader.getSize() > SecurityPolicyYaml.MAX_POLICY_SIZE) {
                return new SecurityPolicyRepositoryFiles.PolicyFileContent(SecurityFrameworkState.PolicyFileProblem.TOO_LARGE, null);
            }
            return new SecurityPolicyRepositoryFiles.PolicyFileContent(null, new String(loader.getBytes(), StandardCharsets.UTF_8));
        }
        catch (IOException _) {
            return new SecurityPolicyRepositoryFiles.PolicyFileContent(SecurityFrameworkState.PolicyFileProblem.UNPARSEABLE, null);
        }
    }

    /**
     * A path whose parent is a symbolic link or a file is not found in a commit, but it is no missing file either: like in a checkout, it is an invalid
     * path that is never repaired.
     */
    private static boolean hasNonDirectoryParent(Repository repository, RevTree tree, String path) throws IOException {
        for (int slash = path.indexOf('/'); slash >= 0; slash = path.indexOf('/', slash + 1)) {
            try (TreeWalk parent = TreeWalk.forPath(repository, path.substring(0, slash), tree)) {
                if (parent == null) {
                    return false;
                }
                if ((parent.getRawMode(0) & FileMode.TYPE_MASK) != FileMode.TYPE_TREE) {
                    return true;
                }
            }
        }
        return false;
    }

    static Optional<String> readFile(Repository repository, RevTree tree, String path) throws IOException {
        try (TreeWalk treeWalk = TreeWalk.forPath(repository, path, tree)) {
            if (treeWalk == null) {
                return Optional.empty();
            }
            return Optional.of(new String(repository.open(treeWalk.getObjectId(0)).getBytes(), StandardCharsets.UTF_8));
        }
    }

    /**
     * @param path a validated policy path
     * @return the content of the policy file in the most recent commit that still contained it as a regular file, if the repository ever had one
     */
    static Optional<String> findPreviousPolicy(Repository repository, String path) {
        try (Git git = new Git(repository)) {
            for (RevCommit commit : git.log().addPath(path).call()) {
                SecurityPolicyRepositoryFiles.PolicyFileContent policy = readPolicyFile(repository, commit.getTree(), path);
                if (policy.content() != null) {
                    return Optional.of(policy.content());
                }
            }
            return Optional.empty();
        }
        catch (GitAPIException e) {
            log.warn("Could not read the policy history of {}, generating a default policy instead", repository.getLocalPath(), e);
            return Optional.empty();
        }
    }
}
