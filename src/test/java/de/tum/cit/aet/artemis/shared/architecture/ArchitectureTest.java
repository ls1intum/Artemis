package de.tum.cit.aet.artemis.shared.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.and;
import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.base.DescribedPredicate.or;
import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.INTERFACES;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleName;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameContaining;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.type;
import static com.tngtech.archunit.core.domain.JavaCodeUnit.Predicates.constructor;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.ConditionEvent.createMessage;
import static com.tngtech.archunit.lang.SimpleConditionEvent.violated;
import static com.tngtech.archunit.lang.conditions.ArchPredicates.are;
import static com.tngtech.archunit.lang.conditions.ArchPredicates.have;
import static com.tngtech.archunit.lang.conditions.ArchPredicates.is;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.constructors;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.members;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noCodeUnits;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.nio.file.Files;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;

import org.awaitility.Awaitility;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.util.SystemReader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.AnnotationCacheOperationSource;
import org.springframework.cache.interceptor.CacheOperation;
import org.springframework.cache.interceptor.CachePutOperation;
import org.springframework.cache.interceptor.CacheableOperation;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hazelcast.core.HazelcastInstance;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaEnumConstant;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaMethodReference;
import com.tngtech.archunit.core.domain.JavaStaticInitializer;
import com.tngtech.archunit.core.domain.properties.HasAnnotations;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.GeneralCodingRules;

import de.tum.cit.aet.artemis.communication.repository.CustomPostRepositoryImpl;
import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.authorization.AuthorizationTestService;
import de.tum.cit.aet.artemis.core.config.ApplicationConfiguration;
import de.tum.cit.aet.artemis.core.config.ConditionalMetricsExclusionConfiguration;
import de.tum.cit.aet.artemis.core.config.JGitConfig;
import de.tum.cit.aet.artemis.core.config.StaticResourcesConfiguration;
import de.tum.cit.aet.artemis.core.repository.base.RepositoryImpl;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.programming.web.repository.RepositoryResource;
import de.tum.cit.aet.artemis.shared.base.AbstractArtemisIntegrationTest;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTestBase;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationJenkinsLocalVCTestBase;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTestBase;

/**
 * This class contains architecture tests that apply for the whole project.
 */
class ArchitectureTest extends AbstractArchitectureTest {

    private static final Logger log = LoggerFactory.getLogger(ArchitectureTest.class);

    @Test
    void testNoJUnit4() {
        ArchRule noJUnit4Imports = noClasses().should().dependOnClassesThat().resideInAPackage("org.junit");
        noJUnit4Imports.check(testClasses);
    }

    @Test
    void testNoGoogleImport() {
        ArchRule noGoogleDependencies = noClasses().should().dependOnClassesThat().resideInAnyPackage("com.google..")
                .because("Google libraries (Guava, Gson) are forbidden to reduce incompatibilities, to reduce dependencies and security risks. " + "Alternatives: "
                        + "Guava Cache -> Spring CacheManager (see HazelcastConfiguration), " + "Guava Collections -> Java Collections API (List.of(), Set.of(), Map.of()), "
                        + "Guava Strings -> Apache Commons Lang3 StringUtils or Spring StringUtils, "
                        + "Guava Preconditions -> for nullness, @NonNull or @Nullable from org.jspecify.annotations (see checkstyle.xml); "
                        + "for any other check, an explicit if throwing IllegalArgumentException or IllegalStateException, " + "Guava Optional -> java.util.Optional, "
                        + "Gson -> Jackson ObjectMapper");
        noGoogleDependencies.check(allClasses);
    }

    @Test
    void testNoShadedDependencies() {
        ArchRule noShadedDependencies = noClasses().should().dependOnClassesThat().resideInAnyPackage("..shaded..", "..repackaged..")
                .because("Depending on a third-party library's shaded/relocated internal copy (e.g. org.apache.velocity.shaded.commons.io.FilenameUtils) is fragile: "
                        + "the host library can drop or relocate the shaded package on any version bump, silently breaking the build (this happened during the OpenSAML/Velocity "
                        + "upgrade in the Spring Boot 4.1 bump). Depend on the real, explicitly-declared library instead (e.g. commons-io for FilenameUtils).");
        noShadedDependencies.check(allClasses);
    }

    @Test
    void testClassNameAndVisibility() {
        ArchRule classNames = methods().that().areAnnotatedWith(Test.class).should().beDeclaredInClassesThat().haveNameMatching(".*Test").orShould().beDeclaredInClassesThat()
                .areAnnotatedWith(Nested.class);
        ArchRule noPublicTestClasses = noClasses().that().haveNameMatching(".*Test").should().bePublic();
        ArchRule noPublicTests = noMethods().that().areAnnotatedWith(Test.class).or().areAnnotatedWith(ParameterizedTest.class).or().areAnnotatedWith(BeforeEach.class).or()
                .areAnnotatedWith(BeforeAll.class).or().areAnnotatedWith(AfterEach.class).or().areAnnotatedWith(AfterAll.class).should().bePublic();

        classNames.check(testClasses);
        noPublicTestClasses.check(testClasses.that(are(not(or(simpleNameContaining("Abstract"), INTERFACES)))));
        noPublicTests.check(testClasses);
    }

    @Test
    void testCorrectStringUtils() {
        ArchRule stringUtils = noClasses().should()
                .dependOnClassesThat(have(simpleName("StringUtils")).and(not(resideInAnyPackage("org.apache.commons.lang3", "org.springframework.util"))));
        ArchRule randomStringUtils = noClasses().should().dependOnClassesThat(have(simpleName("RandomStringUtils")).and(not(resideInAPackage("org.apache.commons.lang3"))));

        stringUtils.check(allClasses);
        randomStringUtils.check(allClasses);
    }

    @Test
    void testNoJGitSystemReaderConfigurationOutsideInitializer() {
        ArchRule setInstanceUsage = noClasses().that().doNotHaveFullyQualifiedName(JGitConfig.class.getName()).should()
                .callMethod(SystemReader.class, "setInstance", SystemReader.class)
                .because("SystemReader#setInstance resets JGit's static platform detection caches (isWindows, isMacOS, isLinux) before re-deriving them, so calling it while "
                        + "other threads run git operations makes those fail with a NullPointerException. Installing it from a @BeforeAll means one call per test class, and test "
                        + "classes run in parallel. Use JGitConfig#configureSystemReaderOnce instead, which is idempotent - the server calls it from its @PostConstruct and GlobalCleanupListener calls it before the test plan starts.");
        setInstanceUsage.check(allClasses);
    }

    @Test
    void testNoJunitJupiterAssertions() {
        ArchRule noJunitJupiterAssertions = noClasses().should().dependOnClassesThat().haveNameMatching("org.junit.jupiter.api.Assertions");

        noJunitJupiterAssertions.check(testClasses);
    }

    @Test
    void testNoCollectorsToList() {
        ArchRule toListUsage = noClasses().should().callMethod(Collectors.class, "toList")
                .because("You should use .toList() or .collect(Collectors.toCollection(ArrayList::new)) instead");
        toListUsage.check(allClasses);
    }

    @Test
    void testRegularExpressionsAreCompiledOnce() {
        String reason = """
                A regular expression has to be compiled before it can be matched, and compiling one is far more \
                expensive than running it. String.matches, String.replaceAll and String.replaceFirst hide that cost: \
                each of them compiles the expression again on every single call, throws the compiled form away, and \
                does so even when the expression is a literal that can never change. Pattern.compile called from a \
                method body has the same problem, only spelled out.
                Declare the expression as a private static final Pattern instead and match against it \
                (PATTERN.matcher(input).matches(), .replaceAll(replacement)), so it is compiled once per class rather \
                than once per call. LocalVCRepositoryUri is the case that prompted this rule: it is constructed on \
                every git request, and its one String.matches call recompiled the same expression every time.""";

        ArchRule noStringRegexShortcuts = noClasses().should().callMethod(String.class, "matches", String.class).orShould()
                .callMethod(String.class, "replaceAll", String.class, String.class).orShould().callMethod(String.class, "replaceFirst", String.class, String.class).orShould()
                .callMethod(Pattern.class, "matches", String.class, CharSequence.class).because(reason);

        ArchRule patternsCompiledInStaticInitializers = classes().should(compileRegularExpressionsOnlyOnce()).because(reason);

        // String.split stays allowed on purpose. It is the one shortcut that does not always compile a pattern: for a
        // single character that is not a regular expression metacharacter - the comma, the slash, the colon that most
        // call sites pass - String.split takes a fast path that never touches Pattern at all.
        noStringRegexShortcuts.check(productionClasses);
        patternsCompiledInStaticInitializers.check(productionClasses);
    }

    /**
     * The methods that may call {@code Pattern.compile} even though they are not a static initializer or a
     * constructor, because the expression they compile is only known at runtime and can therefore not be a constant.
     * <p>
     * Every entry is a place where the expression comes from data rather than from the code: a value stored in the
     * database, a configured property, or an input the method was handed. Adding to this list is a statement that the
     * expression genuinely varies, not that compiling it once was inconvenient.
     */
    private static final Set<String> METHODS_THAT_COMPILE_A_RUNTIME_EXPRESSION = Set.of(
            // the email pattern of an organization, stored per organization
            "de.tum.cit.aet.artemis.account.repository.OrganizationRepository.getAllMatchingOrganizationsByUserEmail(java.lang.String)",
            // the extraction patterns of the identity provider, configured per deployment and compiled once per bean
            "de.tum.cit.aet.artemis.account.security.SAML2Service.generateExtractionPatterns(de.tum.cit.aet.artemis.account.config.SAML2Properties)",
            // the spot of a short answer submission, which the export writes back into the question text
            "de.tum.cit.aet.artemis.admin.service.export.DataExportQuizExerciseCreationService.replaceSpotWithSubmittedAnswer(de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSubmittedAnswer, java.lang.StringBuilder, boolean)",
            // the span that is searched for, assembled from the text being extracted
            "de.tum.cit.aet.artemis.atlas.service.ContentExtractionService.findSpan(java.lang.String, java.lang.String)",
            // the logins mentioned in a post, which differ per post
            "de.tum.cit.aet.artemis.communication.service.PostingService.parseUserMentions(de.tum.cit.aet.artemis.course.domain.Course, java.lang.String)",
            // the placeholder names the caller asks to replace
            "de.tum.cit.aet.artemis.core.util.FileUtil.replacePlaceholderSections(java.nio.file.Path, java.util.Map)",
            // the branch expression configured on the exercise
            "de.tum.cit.aet.artemis.localvc.service.LocalVCServletService.isBranchNameAllowedForRepository(org.eclipse.jgit.lib.Repository, java.lang.String)",
            // the exceptions to filter, which the caller passes in
            "de.tum.cit.aet.artemis.localci.service.ProgrammingExerciseFeedbackCreationService.prepareJVMResultMessageMatcher(java.util.List)");

    /**
     * Complements the {@code callMethod} rules in {@link #testRegularExpressionsAreCompiledOnce()} for
     * {@code Pattern.compile}. A call carries no per-call cost when it runs once for the class or once for the
     * instance, which is what a static initializer and a constructor do: they are where the assignment of a
     * {@code static final} or {@code final Pattern} field ends up. The same call in a method body compiles the
     * expression again on every call.
     * <p>
     * Method references are covered as well, so that {@code Pattern::compile} cannot become a hole in the rule the way
     * it can in a {@code callMethod} rule, which only looks at invocations.
     *
     * @return the condition
     */
    private ArchCondition<JavaClass> compileRegularExpressionsOnlyOnce() {
        return new ArchCondition<>("compile regular expressions in a static initializer or a constructor, so that they are compiled once rather than once per call") {

            @Override
            public void check(JavaClass item, ConditionEvents events) {
                for (JavaMethodCall call : item.getMethodCallsFromSelf()) {
                    if (isPatternCompile(call.getTarget().getName(), call.getTarget().getOwner()) && compilesOncePerCall(call.getOrigin())) {
                        events.add(violated(call, call.getDescription()));
                    }
                }
                for (JavaMethodReference reference : item.getMethodReferencesFromSelf()) {
                    if (isPatternCompile(reference.getTarget().getName(), reference.getTarget().getOwner()) && compilesOncePerCall(reference.getOrigin())) {
                        events.add(violated(reference, reference.getDescription()));
                    }
                }
            }

            private static boolean isPatternCompile(String name, JavaClass owner) {
                return "compile".equals(name) && owner.isEquivalentTo(Pattern.class);
            }

            private static boolean compilesOncePerCall(JavaCodeUnit origin) {
                if (origin instanceof JavaStaticInitializer || origin instanceof JavaConstructor) {
                    return false;
                }
                return !METHODS_THAT_COMPILE_A_RUNTIME_EXPRESSION.contains(origin.getFullName());
            }
        };
    }

    @Test
    void testNoLocaleLessCaseConversion() {
        String reason = "String.toLowerCase() and String.toUpperCase() fold case with the JVM default locale, so the same input gives a different answer depending on where the "
                + "server happens to run. Under a Turkish locale the ASCII letter I lowercases to the dotless \u0131, which turns System.getProperty(\"os.name\").toLowerCase() "
                + "into \"w\u0131ndows\" and makes the Windows branch in WebConfigurer stop matching without any error; every case-insensitive comparison of an identifier, a "
                + "file extension, a MIME type, a header value or a login is unreliable in the same way. Pass the locale explicitly. Locale.ROOT is the default choice, because "
                + "it folds case the same way everywhere, which is what a machine-facing value needs (identifiers, logins, emails, file names and extensions, MIME types, header "
                + "values, enum names, protocol tokens, URL segments, search normalization). Use Locale.ENGLISH only where the surrounding code already does for the same kind of "
                + "value, as User.setLogin does for logins, so that the two agree byte for byte. Where only the comparison matters, equalsIgnoreCase, "
                + "String.CASE_INSENSITIVE_ORDER and Pattern.CASE_INSENSITIVE need no locale at all.";

        // ArchUnit matches a call by its signature, so naming no parameter types addresses the no-argument overloads
        // only: the toLowerCase(Locale) and toUpperCase(Locale) calls that this rule asks for are not matched.
        ArchRule noLocaleLessCalls = noClasses().should().callMethod(String.class, "toLowerCase").orShould().callMethod(String.class, "toUpperCase").because(reason);
        ArchRule noLocaleLessMethodReferences = classes().should(notReferenceLocaleLessCaseConversion()).because(reason);

        // Test classes are checked as well: the sweep that made these calls explicit covered them too, and a test that
        // compares a repository slug or a build plan name is exactly as locale-sensitive as the production code it asserts on.
        noLocaleLessCalls.check(allClasses);
        noLocaleLessMethodReferences.check(allClasses);
    }

    /**
     * Complements {@code callMethod} in {@link #testNoLocaleLessCaseConversion()} for {@code String::toLowerCase} and
     * {@code String::toUpperCase}. A method reference compiles to an invokedynamic rather than to an invocation, so
     * ArchUnit models it as a {@link JavaMethodReference} and not as a {@code JavaMethodCall}, which is what
     * {@code callMethod} looks at. Without this condition a method reference is a hole in the rule.
     *
     * @return the condition
     */
    private ArchCondition<JavaClass> notReferenceLocaleLessCaseConversion() {
        return new ArchCondition<>("not reference String.toLowerCase() or String.toUpperCase() without a locale") {

            @Override
            public void check(JavaClass item, ConditionEvents events) {
                for (JavaMethodReference reference : item.getMethodReferencesFromSelf()) {
                    var target = reference.getTarget();
                    boolean isCaseConversion = "toLowerCase".equals(target.getName()) || "toUpperCase".equals(target.getName());
                    if (isCaseConversion && target.getOwner().isEquivalentTo(String.class) && target.getRawParameterTypes().isEmpty()) {
                        events.add(violated(reference, reference.getDescription()));
                    }
                }
            }
        };
    }

    @Test
    void testTransactionBoundariesOnlyInRepositories() {
        String reason = """
                A transaction boundary may only be declared inside a repository interface, where it lasts for one \
                statement. Declared anywhere else it stays open for the whole call: it holds its locks across every \
                repository call, remote request and file write inside it, blocks anyone who needs those rows for that \
                entire span, and gives two concurrent calls enough overlapping rows to deadlock under load. A \
                self-invoked one is worse than useless, because Spring applies the annotation through a proxy and the \
                call therefore does nothing at all, silently.
                Full rationale: documentation/docs/developer/guidelines/performance.mdx (Avoid Transactions).""";

        // Checked globally, not per module. AbstractModuleRepositoryArchitectureTest carries the same two rules, but
        // only for modules that actually have a subclass — account, calendar, deimos and globalsearch have none, which
        // left 35 services where an annotation would have passed CI unnoticed. A rule that depends on someone
        // remembering to add a per-module test is not an enforced rule, and a module added later would inherit the
        // same hole.
        var repositoryInterfaces = and(INTERFACES, annotatedWith(Repository.class));

        ArchRule methodBoundaries = methods().that().areAnnotatedWith(simpleNameAnnotation("Transactional")).should().beDeclaredInClassesThat(repositoryInterfaces).because(reason);

        // A class-level annotation applies to every method of the class, which is the widest boundary available, and
        // the method rule above cannot see it. It has to demand the same repositoryInterfaces predicate rather than
        // merely @Repository: five concrete classes carry that annotation without being Spring Data interfaces
        // (CustomAuditEventRepository and the four passkey repositories), and a class-level boundary on one of those
        // is an ordinary wide transaction with a repository's name on it.
        ArchRule classBoundaries = noClasses().that(not(repositoryInterfaces)).should().beAnnotatedWith(simpleNameAnnotation("Transactional")).because(reason);

        methodBoundaries.check(productionClasses);
        classBoundaries.check(productionClasses);
    }

    @Test
    void testNoProgrammaticTransactionManagement() {
        String reason = """
                A transaction boundary declared with TransactionTemplate or a PlatformTransactionManager is the same \
                boundary @Transactional declares, only spelled in a way no annotation rule can see — which is exactly \
                how three services kept one after the annotation was banned. It has every cost of the annotated form: \
                the transaction stays open for the whole callback, holding its locks across every repository call and \
                remote request inside it, and two concurrent callbacks with overlapping rows deadlock under load.
                Do the work explicitly instead. To make a check and a write atomic, put the check into the WHERE clause \
                of a @Modifying repository query and act on whether it updated a row — see \
                AnswerPostRepository.verifyIfUnverified. To undo work on failure, compensate in a catch block — see \
                SlideSplitterService.SlideOperation.
                Full rationale: documentation/docs/developer/guidelines/performance.mdx (Avoid Transactions).""";

        // Production only. A test may legitimately need a transaction of its own: to seed data an @Modifying query
        // would have to be invented for, or to hold a row lock while asserting that the code under test waits for it.
        // Everything in org.springframework.transaction is programmatic except the annotation package, which carries
        // @Transactional itself and the enums it takes, and repositories are allowed to use those.
        ArchRule noProgrammaticTransactions = noClasses().should()
                .dependOnClassesThat(resideInAPackage("org.springframework.transaction..").and(not(resideInAPackage("org.springframework.transaction.annotation.."))))
                .because(reason);

        noProgrammaticTransactions.check(productionClasses);
    }

    @Test
    void testNoTransactionSynchronization() {
        String reason = """
                A transaction synchronization callback only runs while a transaction is open, and a transaction boundary may only be \
                declared inside a repository, where it lasts for one statement. Registering a callback from anywhere else therefore \
                does nothing at all: TransactionSynchronizationManager.isSynchronizationActive() is false, so afterCommit and \
                afterCompletion never fire, and nothing is logged. That is the failure mode this rule exists to prevent — code written \
                to delete a file on rollback or to publish an event after commit silently skips both, leaving orphaned files and \
                half-written state behind.
                Do the work explicitly instead. After a repository call returns, its transaction has committed, so "after commit" is \
                simply the next statement. To undo work on failure, compensate in a catch block: see SlideSplitterService.SlideOperation \
                for the pattern, which records the files and rows an operation created and puts them back if it fails.
                Full rationale: documentation/docs/developer/guidelines/performance.mdx (Avoid Transactions).""";

        // Checked over allClasses, tests included: a test that activates synchronization by hand keeps a dead production branch
        // looking covered, which is how the previous usages survived.
        ArchRule noTransactionSynchronization = noClasses().should()
                .dependOnClassesThat(resideInAnyPackage("org.springframework.transaction.support..").and(simpleNameContaining("TransactionSynchronization"))).because(reason);

        noTransactionSynchronization.check(allClasses);
    }

    @Test
    void testNoHibernateSecondLevelCacheAnnotation() {
        String reason = "Hibernate L2 cache is disabled cluster-wide. @Modifying queries bypass L2 invalidation and the absence of service-level @Transactional leaves no clean "
                + "place to coordinate cache eviction within a REST call, both of which produced cross-node stale-read bugs in the multi-node cluster (issue #12574, fixed in PR "
                + "#12578; further cleanup in PR #12579). Use Spring @Cacheable with explicit eviction for DTOs (see FileService for the canonical pattern). "
                + "Full rationale: documentation/docs/developer/guidelines/caching.mdx.";

        ArchRule noClassLevelCache = noClasses().should().beAnnotatedWith("org.hibernate.annotations.Cache").because(reason);
        ArchRule noFieldLevelCache = noFields().should().beAnnotatedWith("org.hibernate.annotations.Cache").because(reason);
        ArchRule noMethodLevelCache = noMethods().should().beAnnotatedWith("org.hibernate.annotations.Cache").because(reason);

        noClassLevelCache.check(productionClasses);
        noFieldLevelCache.check(productionClasses);
        noMethodLevelCache.check(productionClasses);
    }

    /**
     * The association annotations that must not fetch eagerly.
     * <p>
     * {@code @ManyToOne} is deliberately absent. Hibernate cannot make a to-one association lazy without bytecode
     * enhancement or a proxy, and a proxied {@code @ManyToOne} does not work with entity hierarchies - which most of
     * ours are. Its eager default is a fact to design around, not something worth declaring.
     */
    private static final Set<String> ASSOCIATIONS_THAT_MUST_NOT_FETCH_EAGERLY = Set.of("jakarta.persistence.OneToOne", "jakarta.persistence.OneToMany",
            "jakarta.persistence.ManyToMany");

    /**
     * Associations that fetch eagerly today, so that {@link #testNoEagerFetching()} can forbid new ones.
     * <p>
     * The list only shrinks. Turning one lazy is a behaviour change - {@code open-in-view} is disabled, so an
     * association a query did not fetch reads as absent once the session closes - so each needs the code that reads it
     * converted first. Do not add to it.
     */
    private static final Set<String> FIELDS_ALLOWED_TO_FETCH_EAGERLY = Set.of("de.tum.cit.aet.artemis.assessment.domain.AssessmentNote.creator",
            "de.tum.cit.aet.artemis.assessment.domain.Complaint.complaintResponse", "de.tum.cit.aet.artemis.assessment.domain.Complaint.result",
            "de.tum.cit.aet.artemis.assessment.domain.ComplaintResponse.complaint", "de.tum.cit.aet.artemis.assessment.domain.ExampleSubmission.submission",
            "de.tum.cit.aet.artemis.assessment.domain.GradingCriterion.structuredGradingInstructions", "de.tum.cit.aet.artemis.assessment.domain.GradingScale.course",
            "de.tum.cit.aet.artemis.assessment.domain.GradingScale.exam", "de.tum.cit.aet.artemis.assessment.domain.GradingScale.gradeSteps",
            "de.tum.cit.aet.artemis.assessment.domain.Rating.result", "de.tum.cit.aet.artemis.atlas.domain.profile.LearnerProfile.user",
            "de.tum.cit.aet.artemis.communication.domain.AnswerPost.reactions", "de.tum.cit.aet.artemis.communication.domain.Post.answers",
            "de.tum.cit.aet.artemis.communication.domain.Post.plagiarismCase", "de.tum.cit.aet.artemis.communication.domain.Post.reactions",
            "de.tum.cit.aet.artemis.communication.domain.conversation.Channel.exam", "de.tum.cit.aet.artemis.communication.domain.conversation.Channel.exercise",
            "de.tum.cit.aet.artemis.communication.domain.conversation.Channel.lecture", "de.tum.cit.aet.artemis.core.domain.CalendarSubscriptionTokenStore.user",
            "de.tum.cit.aet.artemis.iris.domain.message.IrisMessage.content", "de.tum.cit.aet.artemis.lecture.domain.Attachment.attachmentVideoUnit",
            "de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit.attachment", "de.tum.cit.aet.artemis.lecture.domain.LectureTranscription.lectureUnit",
            "de.tum.cit.aet.artemis.lecture.domain.LectureUnitProcessingState.lectureUnit", "de.tum.cit.aet.artemis.lti.domain.OnlineCourseConfiguration.course",
            "de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCase.post", "de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismSubmission.plagiarismComparison",
            "de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig.programmingExercise",
            "de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation.programmingExercise",
            "de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation.programmingExercise",
            "de.tum.cit.aet.artemis.programming.domain.submissionpolicy.SubmissionPolicy.programmingExercise",
            "de.tum.cit.aet.artemis.quiz.domain.QuizPointStatistic.pointCounters", "de.tum.cit.aet.artemis.quiz.domain.QuizQuestionStatistic.quizQuestion",
            "de.tum.cit.aet.artemis.text.domain.TextBlock.feedback", "de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup.tutorialGroupChannel",
            "de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup.tutorialGroupSchedule", "de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSchedule.tutorialGroup",
            "de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupsConfiguration.course");

    /**
     * No new {@code @OneToOne}, {@code @OneToMany} or {@code @ManyToMany} may be fetched eagerly.
     * <p>
     * An eager association is loaded for every caller, including the majority that never read it, and the cost surfaces
     * nowhere near where it was written. Declare {@code fetch = FetchType.LAZY} and read the association where it is
     * needed, through its own repository - {@code CourseAthenaConfigRepository} is the pattern.
     */
    @Test
    void testNoEagerFetching() {
        ArchRule rule = noFields().that(are(not(allowedToFetchEagerly()))).should(fetchAnAssociationEagerly())
                .because("an eager association is loaded for every caller, including the ones that never read it. Declare fetch = FetchType.LAZY and read it where it is "
                        + "needed, through its own repository. Full rationale: documentation/docs/developer/guidelines/database.mdx");
        rule.check(productionClasses);
    }

    private static DescribedPredicate<JavaField> allowedToFetchEagerly() {
        return DescribedPredicate.describe("allowed to fetch eagerly", field -> FIELDS_ALLOWED_TO_FETCH_EAGERLY.contains(field.getFullName()));
    }

    private static ArchCondition<JavaField> fetchAnAssociationEagerly() {
        return new ArchCondition<>("fetch a @OneToOne, @OneToMany or @ManyToMany eagerly") {

            @Override
            public void check(JavaField field, ConditionEvents events) {
                boolean eager = field.getAnnotations().stream().filter(annotation -> ASSOCIATIONS_THAT_MUST_NOT_FETCH_EAGERLY.contains(annotation.getRawType().getName()))
                        .map(annotation -> annotation.get("fetch")).flatMap(Optional::stream)
                        .anyMatch(fetch -> fetch instanceof JavaEnumConstant constant && "EAGER".equals(constant.name()));
                if (eager) {
                    events.add(SimpleConditionEvent.satisfied(field, createMessage(field, "fetches eagerly")));
                }
            }
        };
    }

    @Test
    void testNoEntityAsCacheValue() {
        String reason = "A cached value is written to the distributed store, so it becomes a wire format, and an entity is the wrong shape for that in three ways. "
                + "It carries whatever its associations reach, which is how a cached list of bookmarks came to hold a User, and with it a password hash and the push "
                + "notification secrets of every bookmarking user: @JsonIgnore does not apply, because map values are encoded by Java serialization and not by Jackson. "
                + "It makes the stored shape change whenever an unrelated entity is refactored, which DistributedDataSurfaceTest then reports as a schema change. And it "
                + "is slower than the query it replaces, because a hit has to deserialize the whole object graph. Project the query into a record of the fields the caller "
                + "actually reads, and load the entity separately where a caller has to write it back. " + "Full rationale: documentation/docs/developer/guidelines/caching.mdx.";

        ArchRule rule = methods().that(storeACacheValue()).should(notReturnAnEntity()).because(reason);

        rule.check(productionClasses);
    }

    /**
     * Selects the methods whose answer is written to a cache.
     * <p>
     * Resolved through {@link AnnotationCacheOperationSource} rather than by looking for the annotations, because
     * {@code @Cacheable} and {@code @CachePut} can also arrive nested inside a {@code @Caching}, and a method annotated
     * that way stores a value just the same. Asking for the effective operations covers both spellings and leaves an
     * eviction-only {@code @Caching} alone, since eviction stores nothing. {@code DistributedDataSurfaceTest} resolves
     * the same question the same way.
     *
     * @return the predicate
     */
    private static DescribedPredicate<JavaMethod> storeACacheValue() {
        AnnotationCacheOperationSource cacheOperationSource = new AnnotationCacheOperationSource(false);
        return new DescribedPredicate<>("store a value in a cache") {

            @Override
            public boolean test(JavaMethod method) {
                Method reflected = method.reflect();
                Collection<CacheOperation> operations = cacheOperationSource.getCacheOperations(reflected, reflected.getDeclaringClass());
                return operations != null && operations.stream().anyMatch(operation -> operation instanceof CacheableOperation || operation instanceof CachePutOperation);
            }
        };
    }

    /**
     * Rejects a cached return type that is, or contains, an entity.
     * <p>
     * The generic type arguments are walked as well, so {@code List<SavedPost>} is caught and not only a bare entity.
     * Reachability beyond the signature is deliberately left to {@code DistributedDataSurfaceTest}, which walks the
     * whole graph and records it: this rule is the one that fails while the code is being written.
     *
     * @return the condition
     */
    private static ArchCondition<JavaMethod> notReturnAnEntity() {
        return new ArchCondition<>("not answer with an entity") {

            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                Set<String> entities = entitiesIn(method.reflect().getGenericReturnType(), new HashSet<>());
                if (!entities.isEmpty()) {
                    events.add(SimpleConditionEvent.violated(method, method.getFullName() + " caches " + String.join(", ", entities)));
                }
            }
        };
    }

    /**
     * Whether the given type carries a JPA mapping, and with it associations rather than plain values.
     * <p>
     * An {@code @Entity} is mapped to a table of its own and a {@code @MappedSuperclass} is not, but both bring the
     * fields and associations that make a value unfit for a cache, so both count here.
     * <p>
     * Asks the JPA annotations rather than the package, because a domain package also holds enums and records, and
     * those are perfectly good cache values: an enum constant has no associations to drag along. The superclasses are
     * walked as well, since an entity that inherits its mapping (a {@code Posting} subclass, say) is just as unsuited.
     *
     * @param type the type to classify
     * @return true when the type is an entity or inherits an entity mapping
     */
    private static boolean isEntity(Class<?> type) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            if (current.isAnnotationPresent(Entity.class) || current.isAnnotationPresent(MappedSuperclass.class)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Collects the entity types that the given type is or contains.
     *
     * @param type    the type to inspect, typically a generic return type
     * @param visited guards against a type variable that refers back to itself
     * @return the names of the entities found, empty when the type is a safe cache value
     */
    private static Set<String> entitiesIn(Type type, Set<Type> visited) {
        if (type == null || !visited.add(type)) {
            return Set.of();
        }
        Set<String> found = new TreeSet<>();
        switch (type) {
            case Class<?> clazz -> {
                if (isEntity(clazz)) {
                    found.add(clazz.getSimpleName());
                }
            }
            case ParameterizedType parameterized -> {
                found.addAll(entitiesIn(parameterized.getRawType(), visited));
                for (Type argument : parameterized.getActualTypeArguments()) {
                    found.addAll(entitiesIn(argument, visited));
                }
            }
            case GenericArrayType array -> found.addAll(entitiesIn(array.getGenericComponentType(), visited));
            case WildcardType wildcard -> {
                for (Type bound : wildcard.getUpperBounds()) {
                    found.addAll(entitiesIn(bound, visited));
                }
            }
            case TypeVariable<?> variable -> {
                for (Type bound : variable.getBounds()) {
                    found.addAll(entitiesIn(bound, visited));
                }
            }
            default -> {
                // A type shape the cache values do not use, so there is nothing to inspect.
            }
        }
        return found;
    }

    @Test
    void testOrderColumnUsage() {
        String reason = "Misuse of @OrderColumn caused production incidents #12574 and #12584. The unidirectional shape "
                + "(@OneToMany + @JoinColumn + @OrderColumn) makes Hibernate DELETE+INSERT the entire child collection on every parent save, "
                + "regenerating primary keys and breaking in-flight references. Required pattern: @OneToMany(mappedBy = \"...\") with the child "
                + "owning the FK via @ManyToOne + @JoinColumn(name = \"...\"), an explicit @OrderColumn(name = \"...\"), and a "
                + "@PrePersist/@PreUpdate hook on the parent that re-asserts child back-references. Prefer Set + @OrderBy on a domain field "
                + "(see Lecture.lectureUnits) when the order can be derived from a domain attribute. "
                + "Full rationale: documentation/docs/developer/guidelines/database.mdx → \"Ordered Collection with Duplicates (List)\".";

        // Rule 1: @OrderColumn must not coexist with @JoinColumn on the same field. The parent's @OneToMany must use mappedBy
        // and let the child @ManyToOne own the FK column. Carrying both annotations is the unidirectional shape that caused #12584.
        ArchRule noUnidirectionalOrderColumn = noFields().that().areAnnotatedWith(OrderColumn.class).should().beAnnotatedWith(JoinColumn.class)
                .because("@OrderColumn must not coexist with @JoinColumn on the same field. " + reason);

        // Rule 2: every @OneToMany + @OrderColumn must have a non-empty mappedBy. Catches the same shape from the other side
        // — fields that omit @JoinColumn but also omit mappedBy are still unidirectional and equally dangerous.
        ArchRule oneToManyOrderColumnHasMappedBy = fields().that().areAnnotatedWith(OrderColumn.class).and().areAnnotatedWith(OneToMany.class).should(haveBidirectionalOneToMany())
                .because("@OneToMany combined with @OrderColumn must specify mappedBy = \"...\" to be bidirectional. " + reason);

        // Rule 3: @OrderColumn must have an explicit `name`. Relying on Hibernate's default-naming heuristic
        // (e.g. <field>_order) is brittle across schema and Hibernate version changes.
        ArchRule orderColumnHasExplicitName = fields().that().areAnnotatedWith(OrderColumn.class).should(haveOrderColumnNameSet())
                .because("@OrderColumn must specify an explicit name attribute. " + reason);

        noUnidirectionalOrderColumn.check(productionClasses);
        oneToManyOrderColumnHasMappedBy.check(productionClasses);
        orderColumnHasExplicitName.check(productionClasses);
    }

    private ArchCondition<JavaField> haveBidirectionalOneToMany() {
        return new ArchCondition<>("have @OneToMany(mappedBy = \"...\") set") {

            @Override
            public void check(JavaField field, ConditionEvents events) {
                JavaAnnotation<?> annotation = findJavaAnnotation(field, OneToMany.class);
                Object mappedBy = annotation.getProperties().get("mappedBy");
                if (!(mappedBy instanceof String value) || value.isBlank()) {
                    events.add(violated(field, field.getFullName() + " is annotated with @OneToMany + @OrderColumn but does not specify mappedBy"));
                }
            }
        };
    }

    private ArchCondition<JavaField> haveOrderColumnNameSet() {
        return new ArchCondition<>("have @OrderColumn(name = \"...\") set") {

            @Override
            public void check(JavaField field, ConditionEvents events) {
                JavaAnnotation<?> annotation = findJavaAnnotation(field, OrderColumn.class);
                Object name = annotation.getProperties().get("name");
                if (!(name instanceof String value) || value.isBlank()) {
                    events.add(violated(field, field.getFullName() + " is annotated with @OrderColumn but does not specify an explicit name"));
                }
            }
        };
    }

    @Test
    void testNullnessAnnotations() {
        // Those are non null annotations for compile time checking. We want to avoid NullPointerExceptions by using those annotations.
        var nonNullPredicate = and(not(resideInPackageAnnotation("org.jspecify.annotations")), simpleNameAnnotation("NonNull"));
        var nullablePredicate = and(not(resideInPackageAnnotation("org.jspecify.annotations")), simpleNameAnnotation("Nullable"));
        // Those are validation annotations. They are used to validate input, e.g. REST request bodies.
        var notNullPredicate = and(not(resideInPackageAnnotation("jakarta.validation.constraints")), simpleNameAnnotation("NonNull"));
        // We want to avoid all other kinds of nullable annotations to ensure consistency.
        var nonnullPredicate = simpleNameAnnotation("Nonnull");

        Set<DescribedPredicate<? super JavaAnnotation<?>>> allPredicates = Set.of(notNullPredicate, nonNullPredicate, nonnullPredicate, nullablePredicate);

        for (var predicate : allPredicates) {
            ArchRule units = noCodeUnits().should().beAnnotatedWith(predicate);
            ArchRule parameters = methods().should(notHaveAnyParameterAnnotatedWith(predicate));

            units.check(allClasses);
            parameters.check(allClasses);
        }
    }

    @Test
    void testValidSimpMessageSendingOperationsUsage() {
        ArchRule usage = fields().that().haveRawType(SimpMessageSendingOperations.class.getTypeName()).should().bePrivate().andShould()
                .beDeclaredIn(WebsocketMessagingService.class)
                .because("Classes should only use WebsocketMessagingService as a Facade and not SimpMessageSendingOperations directly");
        usage.check(productionClasses);
    }

    @Test
    void testFileWriteUsage() {
        ArchRule usage = noClasses().that()
                // The unit test of FileUtil has to plant a file at the destination itself to create the precondition it
                // then asserts on, namely that FileUtil refuses to overwrite it. Going through the helper under test
                // would defeat the test.
                .doNotHaveFullyQualifiedName("de.tum.cit.aet.artemis.core.service.FileUtilUnitTest")
                // FileUtil.publishAtomically is the one place allowed to call Files.move, because an atomic rename is
                // exactly what Apache FileUtils cannot promise: it falls back to copying and deleting, which can leave
                // an incomplete target behind. Callers that need that guarantee go through the helper.
                .and().doNotHaveFullyQualifiedName("de.tum.cit.aet.artemis.core.util.FileUtil").should()
                .callMethodWhere(target(owner(assignableTo(Files.class))).and(target(nameMatching("copy")).or(target(nameMatching("move"))).or(target(nameMatching("write.*")))))
                .because("Files.copy does not create directories if they do not exist. Use Apache FileUtils instead.");
        usage.check(allClasses);
    }

    @Test
    void testLogging() {
        GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING.check(allClasses);

        // We currently need to access standard streams in readTestReports() to use the SurefireReportParser
        // The ParallelConsoleAppender is used to print test logs to the console (necessary due to parallel test execution)
        var classes = allClasses.that(not(simpleName("ProgrammingExerciseTemplateIntegrationTest").or(simpleName("ParallelConsoleAppender"))));
        GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS.check(classes);
    }

    @Test
    void testCorrectLoggerFields() {
        var naming = fields().that().haveRawType(Logger.class).should().haveName("log");
        var modifiers = fields().that().haveRawType(Logger.class).should().bePrivate().andShould().beFinal().andShould().beStatic();

        // Interfaces can only contain public attributes
        // The RepositoryResource inherits its logger
        var modifierExclusions = allClasses.that(are(not(INTERFACES)).and(not(type(RepositoryResource.class))));

        naming.check(allClasses);
        modifiers.check(modifierExclusions);
    }

    @Test
    void testNoJackson2InProductionCode() {
        // Artemis serializes with Jackson 3 (tools.jackson). Jackson 2 stays on the runtime classpath because a
        // dozen third-party libraries carry their own mapper, so nothing stops a new import from compiling — this
        // rule is what keeps one from creeping back in. The annotations are the deliberate exception:
        // jackson-annotations never moved to the tools.jackson group, so @JsonInclude and friends stay where they are.
        noClasses().should().dependOnClassesThat()
                .resideInAnyPackage("com.fasterxml.jackson.databind..", "com.fasterxml.jackson.core..", "com.fasterxml.jackson.dataformat..", "com.fasterxml.jackson.datatype..",
                        "com.fasterxml.jackson.module..", "com.fasterxml.jackson.jr..", "com.fasterxml.jackson.jaxrs..")
                .because("Artemis uses Jackson 3 (tools.jackson); only com.fasterxml.jackson.annotation is still Jackson 2").check(productionClasses);
    }

    @Test
    void testJSONImplementations() {
        // Note: we should only use Jackson. There are rare cases where gson is still used
        noClasses().should().dependOnClassesThat(have(simpleName("JsonObject").or(simpleName("JSONObject"))).and(not(resideInAPackage("com.google.gson")))
                .and(not(resideInAPackage("com.fasterxml.jackson.core"))).and(not(resideInAPackage("tools.jackson.core")))).check(allClasses);
        noClasses().should().dependOnClassesThat(have(simpleName("JsonArray").or(simpleName("JSONArray"))).and(not(resideInAPackage("com.google.gson")))
                .and(not(resideInAPackage("com.fasterxml.jackson.core"))).and(not(resideInAPackage("tools.jackson.core")))).check(allClasses);
        noClasses().should().dependOnClassesThat(have(simpleName("JsonParser").or(simpleName("JSONParser"))).and(not(resideInAPackage("com.google.gson")))
                .and(not(resideInAPackage("com.fasterxml.jackson.core"))).and(not(resideInAPackage("tools.jackson.core")))).check(allClasses);
    }

    @Test
    void testJavaxActivationExclusion() {
        var javaxActivationUsageRule = noClasses().should().dependOnClassesThat().resideInAnyPackage("javax.activation..")
                .because("javax.activation is an outdated library, please use an alternative.");
        var result = javaxActivationUsageRule.evaluate(allClasses);
        assertThat(result.getFailureReport().getDetails()).hasSize(0);
    }

    @Test
    void testGsonExclusion() {
        var gsonUsageRule = noClasses().should().accessClassesThat().resideInAnyPackage("com.google.gson..").because("we use an alternative JSON parsing library.");
        var result = gsonUsageRule.evaluate(allClasses);
        log.debug("Current number of Gson usages: {}", result.getFailureReport().getDetails().size());
        assertThat(result.getFailureReport().getDetails()).hasSize(0);
    }

    @Test
    void testGuavaExclusion() {
        var guavaUsageRule = noClasses().should().accessClassesThat().resideInAnyPackage("com.google.common..")
                .because("Guava is not allowed. Use standard Java or Spring alternatives instead.");
        var result = guavaUsageRule.evaluate(allClasses);
        log.debug("Current number of Guava usages: {}", result.getFailureReport().getDetails().size());
        assertThat(result.getFailureReport().getDetails()).hasSize(0);
    }

    /**
     * Checks that no class directly calls Git.commit(), but instead uses GitService.commit()
     * This is necessary to ensure that committing is identical for all setups, with and without commit signing
     */
    @Test
    void testNoDirectGitCommitCalls() {
        ArchRule usage = noClasses().should().callMethod(Git.class, "commit").because("You should use GitService.commit() instead");
        var classesWithoutGitService = allClasses.that(not(assignableTo(GitService.class)));
        usage.check(classesWithoutGitService);
    }

    @Test
    void testNoHazelcastUsageInConstructors() {
        var notUseHazelcastInConstructor = methods().that().areDeclaredIn(HazelcastInstance.class).should().onlyBeCalled().byCodeUnitsThat(is(not(constructor())))
                .because("Calling Hazelcast during Application startup might be slow since the Network gets used. Use @PostConstruct-methods instead.");
        notUseHazelcastInConstructor.check(allClassesWithHazelcast);
    }

    @Test
    void ensureSpringComponentsAreProfileOrConditionalAnnotated() {
        ArchRule rule = classes().that().areAnnotatedWith(Controller.class).or().areAnnotatedWith(RestController.class).or().areAnnotatedWith(Repository.class).or()
                .areAnnotatedWith(Service.class).or().areAnnotatedWith(Component.class).or().areAnnotatedWith(Configuration.class).and()
                .doNotBelongToAnyOf(ApplicationConfiguration.class, ConditionalMetricsExclusionConfiguration.class).should(beProfileOrConditionalAnnotated())
                .because("we want to be able to exclude these classes from application startup by specifying profiles");

        rule.check(productionClasses);
    }

    @Test
    void testJsonIncludeNonEmpty() {
        members().that().areAnnotatedWith(JsonInclude.class).should(useJsonIncludeNonEmpty()).check(allClasses);
        classes().that().areAnnotatedWith(JsonInclude.class).should(useJsonIncludeNonEmpty()).check(allClasses);
    }

    /**
     * Forbids {@code Class<>} fields (and the raw {@code Class} type) in DTO records and classes.
     * <p>
     * Exposing a {@code Class<? extends SomeEntity>} as a DTO component leaks the JVM/package layout
     * over the wire: serializing it emits a fully-qualified class name, which couples every client
     * (including stale tabs after a refactor) to internal Java package names. The replacement is a
     * dedicated discriminator type — typically an {@code enum} whose values are stable JSON strings
     * via {@code @JsonValue} (see {@link de.tum.cit.aet.artemis.exercise.domain.ExerciseType} and
     * {@link de.tum.cit.aet.artemis.lecture.domain.LectureUnitType}).
     * <p>
     * This rule only inspects <em>fields</em> (which includes synthesized record components). It does
     * <em>not</em> flag {@code Class<>} parameters on JPQL-overloaded constructors that convert the
     * raw entity class produced by Hibernate's {@code TYPE(...)} function into the canonical
     * discriminator field — those are an internal mapping concern that never reaches the wire.
     */
    @Test
    void testNoClassFieldsInDtos() {
        ArchRule rule = noFields().that().areDeclaredInClassesThat().resideInAPackage("..dto..").should().haveRawType(Class.class).because(
                "DTOs must not expose Class<> tokens; that leaks fully-qualified class names over the wire and couples clients to JVM package layout. Use a discriminator enum with @JsonValue instead (see ExerciseType, LectureUnitType).");
        rule.check(productionClasses);
    }

    private <T extends HasAnnotations<T>> ArchCondition<T> useJsonIncludeNonEmpty() {
        return new ArchCondition<>("Use @JsonInclude(JsonInclude.Include.NON_EMPTY)") {

            @Override
            public void check(T item, ConditionEvents events) {
                var annotation = findJavaAnnotation(item, JsonInclude.class);
                var valueProperty = annotation.tryGetExplicitlyDeclaredProperty("value");
                if (valueProperty.isEmpty()) {
                    // @JsonInclude() is ok since it allows explicitly including properties
                    return;
                }
                JavaEnumConstant value = (JavaEnumConstant) valueProperty.get();
                if (!value.name().equals("NON_EMPTY")) {
                    events.add(violated(item, item + " should be annotated with @JsonInclude(JsonInclude.Include.NON_EMPTY)"));
                }
            }
        };
    }

    private static ArchCondition<JavaClass> beProfileOrConditionalAnnotated() {
        return new ArchCondition<>("be annotated with @Profile") {

            @Override
            public void check(JavaClass item, ConditionEvents events) {
                boolean hasProfileAnnotation = item.isAnnotatedWith(Profile.class);
                boolean hasConditionalAnnotation = item.isAnnotatedWith(Conditional.class);
                boolean hasConditionalOnExpression = item.isAnnotatedWith(ConditionalOnExpression.class);
                boolean hasConditionalOnProperty = item.isAnnotatedWith(ConditionalOnProperty.class);
                if (!(hasProfileAnnotation || hasConditionalAnnotation || hasConditionalOnExpression || hasConditionalOnProperty)) {
                    String message = "Class %s is neither annotated with @Profile, @Conditional, @ConditionalOnExpression or @ConditionalOnProperty".formatted(item.getFullName());
                    events.add(SimpleConditionEvent.violated(item, message));
                }
            }
        };
    }

    @Test
    void testNoRestControllersImported() {
        final var exceptions = new String[] { "AccountResourceIntegrationTest", "AdminResourceArchitectureTest", "AndroidAppSiteAssociationResourceTest",
                "AppleAppSiteAssociationResourceTest", "AbstractModuleResourceArchitectureTest", "CommunicationResourceArchitectureTest", "CourseResourceArchitectureTest",
                "LocalCIResourceArchitectureTest", "LocalVCResourceArchitectureTest", "NotificationResourceArchitectureTest", "PlagiarismApiArchitectureTest",
                "LtiApiArchitectureTest", "IrisTutorSuggestionIntegrationTest", "IrisAutonomousTutorPipelineIntegrationTest", "HyperionCodeGenerationResourceTest",
                "HyperionExerciseGenerationResourceTest", "LegacyCalendarResource",
                // Unit tests of the logic a resource performs around its endpoints: the argument validation, the mapping of a
                // failure to a status, and the access checks made inside the method rather than by its annotations. They call
                // the resource directly on purpose; the annotations and the routing stay covered by the integration tests.
                "AuxiliaryRepositoryResourceTest", "BuildJobQueueResourceTest", "ProgrammingExerciseParticipationResourceResetTest", "PublicProgrammingExerciseResultResourceTest",
                "RepositoryProgrammingExerciseParticipationResourceTest" };
        // Resource unit tests exercise validation and ownership checks directly. Their nested parameterized cases belong to the same test, not production code.
        final var resourceBehaviourTests = new String[] { "ProgrammingExerciseCreationResourceMutationGuardTest", "ProgrammingExerciseDeletionResourceMutationGuardTest",
                "ProgrammingExercisePartialUpdateResourceTest", "ProgrammingExerciseTestCaseResourceTest", "ProgrammingExerciseUpdateResourceTest",
                "RepositoryResourceMutationGuardTest", "StaticCodeAnalysisResourceMutationGuardTest", "SubmissionPolicyResourceMutationGuardTest" };
        final var classes = classesAndNestedExcept(classesExcept(allClasses, exceptions), resourceBehaviourTests);
        classes().should(IMPORT_RESTCONTROLLER).check(classes);
    }

    private static final ArchCondition<JavaClass> IMPORT_RESTCONTROLLER = new ArchCondition<>("not import RestController") {

        @Override
        public void check(JavaClass item, ConditionEvents events) {
            item.getDirectDependenciesFromSelf().stream().map(Dependency::getTargetClass).filter(targetClass -> targetClass.isAnnotatedWith(RestController.class))
                    .filter(targetClass -> item.getEnclosingClass().map(c -> !c.equals(targetClass)).orElse(true))
                    .forEach(targetClass -> events.add(violated(item, "%s imports the RestController %s".formatted(item.getName(), targetClass.getName()))));
        }
    };

    @Test
    void shouldNotUserAutowiredAnnotation() {
        ArchRule rule = noFields().should().beAnnotatedWith(Autowired.class).because("fields should not rely on field injection via @Autowired");
        final var exceptions = new Class[] { StaticResourcesConfiguration.class };
        JavaClasses classes = classesExcept(productionClasses, exceptions);
        rule.check(classes);
    }

    @Test
    void shouldNotUseEntityManagerDirectly() {
        // No class should inject EntityManager or EntityManagerFactory directly.
        // All persistence operations must go through Spring Data repositories.
        // Direct EntityManager usage bypasses the repository abstraction, makes code harder to test,
        // and can introduce subtle persistence context bugs (e.g. stale proxies after JPQL bulk operations).
        // See server-development.mdx for details.
        ArchRule rule = noFields().should().haveRawType(jakarta.persistence.EntityManager.class).orShould().haveRawType(jakarta.persistence.EntityManagerFactory.class)
                .because("classes should use Spring Data repositories instead of EntityManager directly. " + "See server-development.mdx for details.");
        // TODO: Refactor these classes to eliminate direct EntityManager usage and remove from this exception list.
        final var exceptions = new Class[] { RepositoryImpl.class, CustomPostRepositoryImpl.class };
        JavaClasses classes = classesExcept(productionClasses, exceptions);
        rule.check(classes);
    }

    @Test
    void shouldNotUseRawJdbcDirectly() {
        // Same reasoning as shouldNotUseEntityManagerDirectly, one level lower: raw JDBC skips the repository layer
        // as well as JPA, and it addresses tables and columns by string. Nothing checks those strings, so a renamed
        // table or column compiles and passes review and only fails when the statement runs.
        // Only the infrastructure that has to exist before any repository does - Liquibase, the schema migration and
        // the data source metrics - may hold a DataSource, and all of it lives in core.config.
        ArchRule rule = noClasses().that().resideOutsideOfPackage("..core.config..").should().dependOnClassesThat()
                .haveFullyQualifiedName("org.springframework.jdbc.core.simple.JdbcClient").orShould().dependOnClassesThat()
                .haveFullyQualifiedName("org.springframework.jdbc.core.JdbcTemplate").orShould().dependOnClassesThat().haveFullyQualifiedName("javax.sql.DataSource")
                .because("classes should use Spring Data repositories instead of raw JDBC. See server-development.mdx for details.");
        rule.check(productionClasses);
    }

    @Test
    void hasMatchingAuthorizationTestClassBeCorrectlyImplemented() throws NoSuchMethodException {
        // Prepare the method that the authorization test should call to be identified as such
        Method allCheckMethod = AuthorizationTestService.class.getMethod("testAllEndpoints", Map.class);
        Method condCheckMethod = AuthorizationTestService.class.getMethod("testConditionalEndpoints", Map.class);
        String identifyingPackage = "authorization";

        // Exclude shared base classes that are not test environments themselves but provide shared code for multiple environments
        ArchRule rule = classes().that(beDirectSubclassOf(AbstractArtemisIntegrationTest.class)).and(not(type(AbstractSpringIntegrationJenkinsLocalVCTestBase.class)))
                .and(not(type(AbstractSpringIntegrationLocalCILocalVCTestBase.class))).and(not(type(AbstractSpringIntegrationIndependentTestBase.class)))
                .should(haveMatchingTestClassCallingAMethod(identifyingPackage, Set.of(allCheckMethod, condCheckMethod)))
                .because("every test environment should have a corresponding authorization test covering the endpoints of this environment.");
        rule.check(testClasses);
    }

    private DescribedPredicate<JavaClass> beDirectSubclassOf(Class<?> clazz) {
        return new DescribedPredicate<>("be implemented in direct subclass of " + clazz.getSimpleName()) {

            @Override
            public boolean test(JavaClass javaClass) {
                var superClasses = javaClass.getAllRawSuperclasses();
                if (superClasses.isEmpty()) {
                    // Tested class has no superclass
                    return false;
                }
                return superClasses.getFirst().getFullName().equals(clazz.getName());
            }
        };
    }

    private ArchCondition<JavaClass> haveMatchingTestClassCallingAMethod(String identifyingPackage, Set<Method> signatureMethods) {
        return new ArchCondition<>("have matching authorization test class") {

            @Override
            public void check(JavaClass item, ConditionEvents events) {
                if (!hasMatchingTestClassCallingMethod(item, identifyingPackage, signatureMethods)) {
                    events.add(violated(item, item.getFullName() + " does not have a matching test class in an \"" + identifyingPackage + "\" package "
                            + "containing a test method that calls any given signature methods"));
                }
            }
        };
    }

    private boolean hasMatchingTestClassCallingMethod(JavaClass javaClass, String identifyingPackage, Set<Method> signatureMethods) {
        var subclasses = javaClass.getSubclasses();
        // Check all subclasses of the given abstract test class to search for an authorization test class
        for (JavaClass subclass : subclasses) {
            // The test class es expected to reside inside an identifying package. We could match the full path, but this is more flexible.
            if (!subclass.getPackageName().contains(identifyingPackage)) {
                continue;
            }
            var methods = subclass.getMethods();
            // Search for a test method that calls a signature method
            for (JavaMethod method : methods) {
                if (!method.isAnnotatedWith(Test.class) && !method.getRawReturnType().reflect().equals(Void.class)) {
                    // Is not a test method
                    continue;
                }
                if (method.getMethodCallsFromSelf().stream()
                        .anyMatch(call -> signatureMethods.stream().anyMatch(checkMethod -> call.getTargetOwner().getFullName().equals(checkMethod.getDeclaringClass().getName())
                                && call.getTarget().getName().equals(checkMethod.getName())))) {
                    // Calls one of the signature methods
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    void ensureSpringComponentsAreLazyAnnotated() {
        ArchRule rule = classes().that().areAnnotatedWith(Controller.class).or().areAnnotatedWith(RestController.class).or().areAnnotatedWith(Repository.class).or()
                .areAnnotatedWith(Service.class).or().areAnnotatedWith(Component.class).or().areAnnotatedWith(Configuration.class)
                // JacksonConfiguration must NOT be lazy because Jackson modules must be available when the ObjectMapper is created
                .and().doNotHaveFullyQualifiedName("de.tum.cit.aet.artemis.core.config.JacksonConfiguration")
                // RequestUtilService must NOT be lazy because it needs the ObjectMapper to be fully configured with Jackson modules
                .and().doNotHaveFullyQualifiedName("de.tum.cit.aet.artemis.core.util.RequestUtilService").should().beAnnotatedWith(Lazy.class)
                .because("All Spring components should be lazy-loaded to improve startup time");

        rule.check(allClasses);
    }

    /**
     * Ensures that @Lazy is not used on constructor or method parameters in Spring beans.
     * <p>
     * Using @Lazy on parameters is often a workaround for circular dependencies, which indicates
     * poor architecture. Instead, @Lazy should only be used at the class level to mark the entire
     * bean as lazy-loaded. If you have a circular dependency, refactor the code to break the cycle.
     * <p>
     * <b>Important:</b> Neither @Lazy on parameters nor ObjectProvider should be used to resolve
     * circular dependencies. Both patterns hide architectural problems and should be avoided.
     *
     * @see #ensureObjectProviderNotUsedForCircularDependencies()
     */
    @Test
    void ensureLazyAnnotationNotUsedOnParameters() {
        ArchRule constructorRule = constructors().that().areDeclaredInClassesThat().areAnnotatedWith(Controller.class).or().areDeclaredInClassesThat()
                .areAnnotatedWith(RestController.class).or().areDeclaredInClassesThat().areAnnotatedWith(Repository.class).or().areDeclaredInClassesThat()
                .areAnnotatedWith(Service.class).or().areDeclaredInClassesThat().areAnnotatedWith(Component.class).or().areDeclaredInClassesThat()
                .areAnnotatedWith(Configuration.class).should(notHaveParametersAnnotatedWithLazy())
                .because("@Lazy should only be used as a class-level annotation, not on constructor parameters. "
                        + "To lazily inject a dependency, mark the dependency class itself with @Lazy instead.");

        ArchRule methodRule = methods().that().areDeclaredInClassesThat().areAnnotatedWith(Controller.class).or().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
                .or().areDeclaredInClassesThat().areAnnotatedWith(Repository.class).or().areDeclaredInClassesThat().areAnnotatedWith(Service.class).or().areDeclaredInClassesThat()
                .areAnnotatedWith(Component.class).or().areDeclaredInClassesThat().areAnnotatedWith(Configuration.class).should(notHaveMethodParametersAnnotatedWithLazy())
                .because("@Lazy should only be used as a class-level annotation, not on method parameters. "
                        + "To lazily inject a dependency, mark the dependency class itself with @Lazy instead.");

        constructorRule.check(productionClasses);
        methodRule.check(productionClasses);
    }

    private ArchCondition<JavaConstructor> notHaveParametersAnnotatedWithLazy() {
        return new ArchCondition<>("not have parameters annotated with @Lazy") {

            // JPA entity listeners are instantiated by Hibernate during EntityManagerFactory construction,
            // before the full Spring context is available. Using @Lazy on constructor parameters is the
            // only way to break the circular dependency: EntityManagerFactory → EntityListener → Services
            // → Repositories → EntityManagerFactory. This exception should NOT be extended to other classes.
            private static final Set<String> JPA_ENTITY_LISTENER_EXCEPTIONS = Set.of("de.tum.cit.aet.artemis.assessment.ResultListener");

            @Override
            public void check(JavaConstructor constructor, ConditionEvents events) {
                if (JPA_ENTITY_LISTENER_EXCEPTIONS.contains(constructor.getOwner().getFullName())) {
                    return;
                }
                for (var parameter : constructor.getParameters()) {
                    if (parameter.isAnnotatedWith(Lazy.class)) {
                        events.add(violated(constructor,
                                ("Constructor %s has parameter '%s' annotated with @Lazy. "
                                        + "Remove @Lazy from the parameter and ensure the injected bean class is annotated with @Lazy instead.")
                                        .formatted(constructor.getFullName(), parameter.getRawType().getSimpleName())));
                    }
                }
            }
        };
    }

    private ArchCondition<JavaMethod> notHaveMethodParametersAnnotatedWithLazy() {
        return new ArchCondition<>("not have parameters annotated with @Lazy") {

            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                for (var parameter : method.getParameters()) {
                    if (parameter.isAnnotatedWith(Lazy.class)) {
                        events.add(violated(method,
                                ("Method %s has parameter '%s' annotated with @Lazy. "
                                        + "Remove @Lazy from the parameter and ensure the injected bean class is annotated with @Lazy instead.")
                                        .formatted(method.getFullName(), parameter.getRawType().getSimpleName())));
                    }
                }
            }
        };
    }

    /**
     * Ensures that ObjectProvider is not used in constructor parameters of Spring beans.
     * <p>
     * ObjectProvider can be misused as a workaround for circular dependencies, similar to @Lazy
     * on parameters. This hides architectural problems and should be avoided.
     * <p>
     * <b>Allowed uses:</b>
     * <ul>
     * <li>In @Bean method parameters within @Configuration classes (standard Spring pattern)</li>
     * <li>For genuinely optional dependencies where getIfAvailable() is used</li>
     * <li>In health indicators that need to check if a service exists</li>
     * </ul>
     * <p>
     * <b>Important:</b> Neither @Lazy on parameters nor ObjectProvider should be used to resolve
     * circular dependencies. Both patterns hide architectural problems and should be avoided.
     *
     * @see #ensureLazyAnnotationNotUsedOnParameters()
     */
    @Test
    void ensureObjectProviderNotUsedForCircularDependencies() {
        ArchRule rule = constructors().that().areDeclaredInClassesThat().areAnnotatedWith(Controller.class).or().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
                .or().areDeclaredInClassesThat().areAnnotatedWith(Repository.class).or().areDeclaredInClassesThat().areAnnotatedWith(Service.class).or().areDeclaredInClassesThat()
                .areAnnotatedWith(Component.class)
                // Exclude @Configuration classes - ObjectProvider in @Bean methods is a standard Spring pattern
                .and().areDeclaredInClassesThat().areNotAnnotatedWith(Configuration.class).should(notHaveObjectProviderParameters())
                .because("ObjectProvider should not be used in constructor parameters to work around circular dependencies. "
                        + "If you have a circular dependency, refactor the code to break the cycle. "
                        + "ObjectProvider is only acceptable for genuinely optional dependencies (using getIfAvailable()).");

        rule.check(productionClasses);
    }

    private ArchCondition<JavaConstructor> notHaveObjectProviderParameters() {
        return new ArchCondition<>("not have ObjectProvider parameters") {

            @Override
            public void check(JavaConstructor constructor, ConditionEvents events) {
                for (var parameter : constructor.getParameters()) {
                    String typeName = parameter.getRawType().getName();
                    if (typeName.equals("org.springframework.beans.factory.ObjectProvider")) {
                        events.add(violated(constructor,
                                ("Constructor %s has parameter of type ObjectProvider. " + "ObjectProvider should not be used to work around circular dependencies. "
                                        + "Refactor the code to break the dependency cycle instead.").formatted(constructor.getFullName())));
                    }
                }
            }
        };
    }

    @Test
    void testAsyncTestShouldWait() {
        ArchRule rule = methods().that(areInIntegrationTests()).and(callAnAsyncMethod()).should(callAWaitMethod()).because("tests should wait for async effects");
        rule.check(testClasses);
    }

    private static DescribedPredicate<JavaMethod> callAnAsyncMethod() {
        return new DescribedPredicate<>("call a method annotated with async") {

            @Override
            public boolean test(JavaMethod javaMethod) {
                var asyncCalls = javaMethod.getMethodCallsFromSelf().stream().filter(call -> call.getTarget().isAnnotatedWith(Async.class));

                var firstVerifyLineNumberOptional = javaMethod.getMethodCallsFromSelf().stream()
                        .filter(call -> call.getTarget().getName().equals("verify") && call.getTargetOwner().getFullName().equals("org.mockito.Mockito"))
                        .mapToInt(JavaAccess::getLineNumber).min();

                if (firstVerifyLineNumberOptional.isEmpty()) {
                    return asyncCalls.findAny().isPresent();
                }

                // method calls on and after a verify() line are usually not calls on the actual object
                var firstVerifyLineNumber = firstVerifyLineNumberOptional.orElseThrow();
                return asyncCalls.anyMatch(call -> call.getLineNumber() < firstVerifyLineNumber);
            }
        };
    }

    private static DescribedPredicate<JavaMethod> areInIntegrationTests() {

        return new DescribedPredicate<>("are in integration tests") {

            @Override
            public boolean test(JavaMethod javaMethod) {
                return javaMethod.getOwner().isAssignableTo(AbstractArtemisIntegrationTest.class);
            }
        };
    }

    private static ArchCondition<JavaMethod> callAWaitMethod() {
        var isDirectlyWaiting = callMethod(Mockito.class, "timeout").or(callMethod(Mockito.class, "after")).or(callMethod(Awaitility.class, "await"));
        var isWaiting = new DescribedPredicate<JavaCall<?>>("is waiting") {

            @Override
            public boolean test(JavaCall<?> call) {
                if (isDirectlyWaiting.test(call)) {
                    return true;
                }
                var target = call.getTarget().resolveMember();
                if (target.isPresent() && target.get() instanceof JavaMethod targetMethod) {
                    return targetMethod.getMethodCallsFromSelf().stream().anyMatch(isDirectlyWaiting);
                }
                return false;
            }
        };

        return new ArchCondition<>("call a wait method") {

            @Override
            public void check(JavaMethod item, ConditionEvents events) {
                boolean doesNotWait = item.getMethodCallsFromSelf().stream().noneMatch(isWaiting);
                if (doesNotWait) {
                    events.add(violated(item, createMessage(item, "does not call a wait method")));
                }
            }
        };
    }

    private static DescribedPredicate<JavaCall<?>> callMethod(Class<?> owner, String methodName) {
        return JavaCall.Predicates.target(owner(type(owner))).and(JavaCall.Predicates.target(name(methodName)));
    }

    @Test
    void testUsageOfSchedulingClasses() {
        // Classes that are not itself part of the scheduling profile
        // should use classes with scheduling profile annotation only in an optional context.
        // We check this using constructors (constructor injection) since otherwise usages of the optional itself would be detected
        constructors().that().areDeclaredInClassesThat(and(annotatedWith(Profile.class), not(classWithSchedulingProfile()))).should(correctlyUseSchedulingParameters())
                .check(productionClasses);
    }

    private DescribedPredicate<JavaClass> classWithSchedulingProfile() {
        return new DescribedPredicate<>("have scheduling profile") {

            @Override
            public boolean test(JavaClass javaClass) {
                var profiles = getProfiles(javaClass);
                for (String profile : profiles) {
                    if (profile.contains("scheduling") && !profile.contains("!scheduling")) {
                        return true;
                    }
                }
                return false;
            }
        };
    }

    private String[] getProfiles(JavaClass javaClass) {
        if (!javaClass.isAnnotatedWith(Profile.class)) {
            return new String[0];
        }
        Profile profile = javaClass.getAnnotationOfType(Profile.class);
        return profile.value();
    }

    private ArchCondition<JavaConstructor> correctlyUseSchedulingParameters() {
        return new ArchCondition<>("correctly wrap scheduling dependencies in optionals") {

            @Override
            public void check(JavaConstructor item, ConditionEvents events) {
                var parameters = item.getParameters();
                for (var parameter : parameters) {
                    if (classWithSchedulingProfile().test(parameter.getRawType())) {
                        events.add(violated(parameter,
                                "Class %s uses class %s without wrapping it with Optionals.".formatted(parameter.getOwner().getFullName(), parameter.getType().getName())));
                    }
                }
            }
        };
    }

    @Test
    void ensureOnlyLectureClassIsUpdatingUnitOrder() {
        ArchRule rule = methods().that().haveName("setLectureUnitOrder").and().areDeclaredIn(LectureUnit.class).should(onlyBeCalledBy(Lecture.class))
                .because("Only Lecture class should manage the order of lecture units");

        rule.check(allClasses);
    }

    private ArchCondition<JavaMethod> onlyBeCalledBy(Class<?> allowedCaller) {
        return new ArchCondition<>("only be called by " + allowedCaller.getSimpleName()) {

            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                Set<JavaMethodCall> calls = method.getCallsOfSelf();
                for (JavaMethodCall call : calls) {
                    JavaClass caller = call.getOriginOwner();
                    if (!caller.isAssignableTo(allowedCaller)) {
                        events.add(violated(call,
                                "%s calls %s, but only %s should call this method".formatted(caller.getName(), method.getFullName(), allowedCaller.getSimpleName())));
                    }
                }
            }
        };
    }
}
