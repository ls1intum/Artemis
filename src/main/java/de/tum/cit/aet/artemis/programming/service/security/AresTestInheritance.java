package de.tum.cit.aet.artemis.programming.service.security;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * How test classes inherit test methods from the test classes they extend, as far as the annotations that Ares applies depend on it: which method of a
 * superclass a subclass overrides, and which it runs as its own test.
 */
final class AresTestInheritance {

    private AresTestInheritance() {
    }

    /**
     * The class a type extends, among the types of the repository: like Java, the first candidate that exists wins, so a class of the type's own package
     * takes precedence over one of a wildcard import.
     */
    static Optional<String> superclassOf(AresTestSources.TypeDeclaration type, Set<String> allTypeNames) {
        return type.superclass().stream().filter(allTypeNames::contains).findFirst();
    }

    /**
     * A method annotation that Ares applies to a test the class runs.
     *
     * @param inherited whether the class inherits the test method from another test class instead of declaring it
     */
    record MethodPolicyInEffect(AresPolicyAnnotations.Annotation annotation, boolean inherited) {
    }

    /**
     * Ares applies the annotation of a test method instead of the one of its class, also to the test methods a class inherits from other test classes, because
     * it reads the annotation of the method where it is declared before the one of the class that runs it, unless a class in between overrides the method.
     * An active one in a class whose annotation switches the sandbox off runs that test with the sandbox, and an inherited one keeps the test of the subclass
     * on another policy than its class. Neither the state of the class nor its assignments could show that, so such a class is not supported.
     *
     * @return the method annotations Ares applies to the tests the class runs: its own and those of the test methods it inherits without an override
     */
    static List<MethodPolicyInEffect> methodPoliciesInEffect(AresTestSources.TypeDeclaration testClass, Map<String, AresTestSources.TypeDeclaration> testClassesByName,
            Map<String, AresTestSources.TypeDeclaration> typesByName) {
        Hierarchy hierarchy = Hierarchy.of(testClass, testClassesByName, typesByName);
        List<MethodPolicyInEffect> policies = new ArrayList<>();
        for (int index = 0; index < hierarchy.chain().size(); index++) {
            AresTestSources.TypeDeclaration type = hierarchy.chain().get(index);
            for (Map.Entry<String, AresPolicyAnnotations.Annotation> methodPolicy : type.methodPolicies().entrySet()) {
                if (!isOverridden(type, methodPolicy.getKey(), hierarchy.chain().subList(0, index), hierarchy.memberTypes())) {
                    policies.add(new MethodPolicyInEffect(methodPolicy.getValue(), index > 0));
                }
            }
        }
        return policies;
    }

    /**
     * The test methods of a test class that other test classes run as their own tests, because they extend it without overriding the methods. Ares applies
     * the annotations of these methods also when a subclass runs them, so changing them changes the policy of the subclass.
     *
     * @param testClasses the test classes of the repository, see {@link SecurityPolicyRepositoryFiles#testClasses(List)}
     * @param typesByName the top-level types of the repository by their qualified name
     * @return the names of these test methods by the qualified name of the class that declares them; a class without such methods is left out
     */
    static Map<String, Set<String>> testMethodsRunBySubclasses(List<AresTestSources.TypeDeclaration> testClasses, Map<String, AresTestSources.TypeDeclaration> typesByName) {
        Map<String, AresTestSources.TypeDeclaration> testClassesByName = new HashMap<>();
        testClasses.forEach(testClass -> testClassesByName.put(testClass.qualifiedName(), testClass));
        Map<String, Set<String>> runBySubclasses = new HashMap<>();
        for (AresTestSources.TypeDeclaration testClass : testClasses) {
            Hierarchy hierarchy = Hierarchy.of(testClass, testClassesByName, typesByName);
            for (int index = 1; index < hierarchy.chain().size(); index++) {
                AresTestSources.TypeDeclaration type = hierarchy.chain().get(index);
                List<AresTestSources.TypeDeclaration> subclasses = hierarchy.chain().subList(0, index);
                type.testSignatures().stream().map(AresTestCases.TestSignature::name).filter(method -> !isOverridden(type, method, subclasses, hierarchy.memberTypes()))
                        .forEach(method -> runBySubclasses.computeIfAbsent(type.qualifiedName(), _ -> new HashSet<>()).add(method));
            }
        }
        return runBySubclasses;
    }

    /**
     * A test class with the test classes it extends.
     *
     * @param chain       the test class first, then the class it extends and so on, as long as they are test classes
     * @param memberTypes the simple names of the member types in scope in each class of the chain, by its qualified name; empty where unknown
     */
    private record Hierarchy(List<AresTestSources.TypeDeclaration> chain, Map<String, Optional<Set<String>>> memberTypes) {

        static Hierarchy of(AresTestSources.TypeDeclaration testClass, Map<String, AresTestSources.TypeDeclaration> testClassesByName,
                Map<String, AresTestSources.TypeDeclaration> typesByName) {
            List<AresTestSources.TypeDeclaration> chain = new ArrayList<>();
            Set<String> visited = new HashSet<>();
            Optional<AresTestSources.TypeDeclaration> declaring = Optional.of(testClass);
            // Like Java, the first candidate of the superclass that exists wins, also if it is no test class, which ends the chain of test classes.
            while (declaring.isPresent() && visited.add(declaring.get().qualifiedName())) {
                chain.add(declaring.get());
                declaring = superclassOf(declaring.get(), typesByName.keySet()).map(testClassesByName::get);
            }
            // A member type shadows an import of the same simple name in the class that declares or inherits it, not in its superclasses.
            Map<String, Optional<Set<String>>> memberTypes = new HashMap<>();
            chain.forEach(type -> memberTypes.put(type.qualifiedName(), AresTestSources.memberTypesInScope(type, typesByName)));
            return new Hierarchy(List.copyOf(chain), memberTypes);
        }
    }

    /**
     * Whether a subclass overrides the test method, so that JUnit runs the override with its own annotations instead: it declares a test method with the
     * same signature, and is in the same package unless the method is public or protected. A name that several test methods of the type share is never
     * overridden as a whole, so it counts as inherited, and so does a signature with a simple type name that may name different types in the two files,
     * e.g. through wildcard imports of {@code java.util.*} and {@code java.sql.*}, or that a member type of the class hierarchy may shadow, also an unknown one
     * of a supertype outside the repository.
     *
     * @param subclasses  the classes between the class that runs the tests and the type, including the former
     * @param memberTypes the simple names of the member types in scope in each class of the hierarchy, by its qualified name; empty where unknown
     */
    private static boolean isOverridden(AresTestSources.TypeDeclaration type, String method, List<AresTestSources.TypeDeclaration> subclasses,
            Map<String, Optional<Set<String>>> memberTypes) {
        List<AresTestCases.TestSignature> signatures = type.testSignatures().stream().filter(signature -> signature.name().equals(method)).toList();
        if (signatures.size() != 1) {
            return false;
        }
        AresTestCases.TestSignature signature = signatures.getFirst();
        if (signature.dependsOnPackage() || !importsAreNotShadowed(signature, type, memberTypes)) {
            return false;
        }
        for (AresTestSources.TypeDeclaration subclass : subclasses) {
            boolean canOverride = signature.visibleInOtherPackages() || subclass.packageName().equals(type.packageName());
            if (canOverride && declares(subclass, signature, memberTypes)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return whether the class declares a test method with the signature, whose type names really name the types the signature names
     */
    private static boolean declares(AresTestSources.TypeDeclaration type, AresTestCases.TestSignature signature, Map<String, Optional<Set<String>>> memberTypes) {
        return type.testSignatures().stream().anyMatch(candidate -> candidate.text().equals(signature.text()) && importsAreNotShadowed(candidate, type, memberTypes));
    }

    /**
     * @param declaringType the class that declares the signature
     * @param memberTypes   the member types in scope in each class of the hierarchy, see {@link #isOverridden}
     * @return whether every type name of the signature that an import resolves really names the imported type: no member type in scope of the class, also no
     *         unknown one of a supertype outside the repository, shadows it
     */
    private static boolean importsAreNotShadowed(AresTestCases.TestSignature signature, AresTestSources.TypeDeclaration declaringType,
            Map<String, Optional<Set<String>>> memberTypes) {
        return signature.importedNames().isEmpty()
                || memberTypes.get(declaringType.qualifiedName()).map(types -> signature.importedNames().stream().noneMatch(types::contains)).orElse(false);
    }
}
