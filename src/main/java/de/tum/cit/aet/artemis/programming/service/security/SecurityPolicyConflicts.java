package de.tum.cit.aet.artemis.programming.service.security;

import static de.tum.cit.aet.artemis.programming.service.security.SecurityFrameworkService.ENTITY_NAME;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.security.SecurityFrameworkState;

/**
 * The checks that a change of the policy editor is still based on the policies in the tests repository: the change rewrites the policy files and the
 * {@code @Policy} annotations from the state the editor loaded, so a commit that changed them since makes it a conflict.
 */
final class SecurityPolicyConflicts {

    private SecurityPolicyConflicts() {
    }

    /**
     * A retry only succeeds while the policies are still as the commit of the change left them. A later commit that reverts or changes them supersedes the
     * change, e.g. another instructor's, so the retry fails as a conflict instead of reporting that commit as the result; a commit that changes something
     * else, e.g. the body of a test, keeps it. Like a first attempt, see {@link #requireUnchangedSince(Repository, String)}, it fails if another Ares version
     * was pinned since.
     */
    static void requireStillApplied(Repository repository, String appliedCommit) {
        try {
            requireUnchangedSince(repository, appliedCommit);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Refuses a change if a policy changed in the repository since the editor loaded the policies: a policy file, which test classes point to which file, an
     * annotation of a test class or test method, e.g. a new override without a policy file, or the entries of a policy, e.g. a test added to a class assigned
     * case by case, because the change rewrites the annotations from the state it was loaded with, or the pinned Ares version, because the review checked the
     * policies against the version of the loaded commit. Commits that change something else, e.g. the body of a test, do not conflict. An id that names no
     * commit, e.g. a file of the repository, conflicts like a commit that no longer exists.
     */
    static void requireUnchangedSince(Repository repository, String baseCommitHash) throws IOException {
        ObjectId head = repository.resolve(Constants.HEAD);
        if (head != null && head.name().equals(baseCommitHash)) {
            return;
        }
        ObjectId base = repository.resolve(baseCommitHash);
        try (RevWalk revWalk = new RevWalk(repository)) {
            if (base == null || head == null || !repository.getObjectDatabase().has(base)) {
                throw policiesChanged();
            }
            RevTree baseTree = parseCommit(revWalk, base).getTree();
            RevTree headTree = revWalk.parseCommit(head).getTree();
            SecurityFrameworkState baseState = SecurityFrameworkCommitFiles.evaluate(repository, baseTree);
            SecurityFrameworkState headState = SecurityFrameworkCommitFiles.evaluate(repository, headTree);
            if (!policyReferences(baseState).equals(policyReferences(headState)) || !assignmentState(repository, baseTree).equals(assignmentState(repository, headTree))) {
                throw policiesChanged();
            }
            // The review checked the policies against the version of the loaded commit and showed the build file that pins it, so neither may have changed since.
            if (!SecurityFrameworkCommitFiles.aresVersion(repository, baseTree).equals(SecurityFrameworkCommitFiles.aresVersion(repository, headTree))
                    || !SecurityFrameworkCommitFiles.aresBuildFile(repository, baseTree).equals(SecurityFrameworkCommitFiles.aresBuildFile(repository, headTree))) {
                throw policiesChanged();
            }
            for (SecurityFrameworkState.PolicyFile policyFile : headState.policyFiles()) {
                Optional<String> path = SecurityPolicyRepositoryFiles.validatePolicyPath(policyFile.policyPath());
                if (path.isPresent() && !Objects.equals(SecurityFrameworkCommitFiles.readPolicyFile(repository, baseTree, path.get()).content(),
                        SecurityFrameworkCommitFiles.readPolicyFile(repository, headTree, path.get()).content())) {
                    throw policiesChanged();
                }
            }
        }
    }

    /**
     * Refuses a change that assigns some test cases of a class assigned as a whole if the class got test cases since the editor loaded the policies. They use
     * the policy of the class, and splitting the class would release them although the editor never showed them. A test added to a class that stays assigned
     * as a whole, or that is released as a whole, keeps following the class and does not conflict.
     */
    static void requireKnownTestCasesOfSplitClasses(Repository repository, String baseCommitHash, Map<String, SecurityTestAssignments.TestClass> testClasses,
            Map<String, String> requested) throws IOException {
        Set<String> assignedCaseByCase = new HashSet<>();
        for (String entry : requested.keySet()) {
            if (entry.indexOf(SecurityTestAssignments.METHOD_SEPARATOR) >= 0) {
                assignedCaseByCase.add(SecurityTestAssignments.testClassOf(entry));
            }
        }
        List<SecurityTestAssignments.TestClass> split = new ArrayList<>();
        for (SecurityTestAssignments.TestClass testClass : testClasses.values()) {
            if (testClass.entries().containsKey(testClass.qualifiedName()) && assignedCaseByCase.contains(testClass.qualifiedName())) {
                split.add(testClass);
            }
        }
        ObjectId head = repository.resolve(Constants.HEAD);
        if (split.isEmpty() || (head != null && head.name().equals(baseCommitHash))) {
            return;
        }
        ObjectId base = repository.resolve(baseCommitHash);
        if (base == null) {
            throw policiesChanged();
        }
        try (RevWalk revWalk = new RevWalk(repository)) {
            RevTree baseTree = parseCommit(revWalk, base).getTree();
            Map<String, SecurityTestAssignments.TestClass> loaded = SecurityTestAssignments.read(SecurityFrameworkCommitFiles.javaSources(repository, baseTree));
            for (SecurityTestAssignments.TestClass testClass : split) {
                SecurityTestAssignments.TestClass loadedClass = loaded.get(testClass.qualifiedName());
                if (loadedClass == null || !new HashSet<>(loadedClass.testCases()).containsAll(testClass.testCases())) {
                    throw policiesChanged();
                }
            }
        }
    }

    /**
     * The {@code @Policy} annotations of a test class, without its test cases, which a change of the tests may change without changing a policy.
     *
     * @param active            whether its annotation enforces a policy
     * @param classPolicyPath   the policy file of its annotation
     * @param methodPolicyPaths the policy files of the annotations of its test methods
     * @param defaultMethods    the test methods whose annotation gives them the strict default policy
     * @param switchedOff       the test methods whose annotation switches the sandbox off
     * @param otherArguments    the other arguments of the annotations, e.g. {@code withinPath}, by the test method they are on and the empty string for the class
     */
    private record Annotations(boolean active, @Nullable String classPolicyPath, Map<String, String> methodPolicyPaths, Set<String> defaultMethods, Set<String> switchedOff,
            Map<String, Map<String, String>> otherArguments) {
    }

    /**
     * The state of the {@code @Policy} annotations of a commit that a change is based on: the annotations of each test class and the entries they give each
     * policy. A test added to a class assigned case by case changes the entries, because the change would assign the class without it; a test added to a
     * class assigned as a whole does not, and neither does the body of a test.
     */
    private record AssignmentState(Map<String, Annotations> annotations, Map<String, List<String>> entries) {
    }

    private static AssignmentState assignmentState(Repository repository, RevTree tree) throws IOException {
        Map<String, String> javaSources = SecurityFrameworkCommitFiles.javaSources(repository, tree);
        Map<String, SecurityTestAssignments.TestClass> testClasses = SecurityTestAssignments.read(javaSources);
        Map<String, Annotations> annotations = new HashMap<>();
        for (SecurityTestAssignments.TestClass testClass : testClasses.values()) {
            Map<String, Map<String, String>> arguments = otherArguments(testClass.sourcePath(), javaSources.get(testClass.sourcePath()));
            Annotations classAnnotations = new Annotations(testClass.active(), testClass.classPolicyPath(), testClass.methodPolicyPaths(), testClass.defaultMethods(),
                    testClass.switchedOff(), arguments);
            annotations.put(testClass.qualifiedName(), classAnnotations);
        }
        return new AssignmentState(annotations, SecurityTestAssignments.entriesByPolicy(testClasses.values()));
    }

    /**
     * @return the arguments of the {@code @Policy} annotations of the test class in the file other than its policy file and switch, e.g. {@code withinPath},
     *         by the test method they are on and the empty string for the class
     */
    private static Map<String, Map<String, String>> otherArguments(String path, String source) {
        Map<String, Map<String, String>> arguments = new HashMap<>();
        AresTestSources.relevantType(AresTestSources.analyze(path, source)).ifPresent(type -> {
            type.policies().forEach(annotation -> arguments.put("", AresPolicyAnnotations.otherArguments(annotation)));
            type.methodPolicies().forEach((method, annotation) -> arguments.put(method, AresPolicyAnnotations.otherArguments(annotation)));
        });
        return arguments;
    }

    private static Map<String, List<String>> policyReferences(SecurityFrameworkState state) {
        return state.policyFiles().stream().collect(Collectors.toMap(SecurityFrameworkState.PolicyFile::policyPath, SecurityFrameworkState.PolicyFile::referencedBy));
    }

    /**
     * @return the commit the editor loaded the policies from
     * @throws ConflictException if the id names another object of the repository, e.g. a file, so the editor cannot have loaded it
     */
    static RevCommit parseCommit(RevWalk revWalk, ObjectId id) throws IOException {
        try {
            return revWalk.parseCommit(id);
        }
        catch (IncorrectObjectTypeException _) {
            throw policiesChanged();
        }
    }

    private static ConflictException policiesChanged() {
        return new ConflictException("The security policies changed in the tests repository after the editor loaded them. Reload them and try again.", ENTITY_NAME,
                "securityPolicyChanged");
    }
}
