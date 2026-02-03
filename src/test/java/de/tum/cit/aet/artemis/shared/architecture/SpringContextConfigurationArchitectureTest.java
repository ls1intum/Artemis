package de.tum.cit.aet.artemis.shared.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import de.tum.cit.aet.artemis.shared.base.AbstractArtemisBuildAgentTest;
import de.tum.cit.aet.artemis.shared.base.AbstractArtemisIntegrationTest;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationJenkinsLocalVCBatchTest;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationJenkinsLocalVCTemplateTest;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationJenkinsLocalVCTest;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationJenkinsLocalVCTestBase;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalVCSamlTest;

/**
 * Architecture tests to ensure Spring context configuration is only defined in allowed base test classes.
 * <p>
 * Spring Boot caches test application contexts and reuses them when configurations match.
 * When {@code @MockitoSpyBean}, {@code @MockitoBean}, {@code @TestPropertySource}, or {@code @ActiveProfiles}
 * annotations differ between test classes, Spring creates separate contexts, causing additional server starts.
 * <p>
 * We enforce a maximum of 8 server starts during test execution via the GitHub Action in
 * {@code supporting_scripts/extract_number_of_server_starts.sh}.
 *
 * @see <a href="https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/ctx-management/caching.html">Spring Test Context Caching</a>
 */
class SpringContextConfigurationArchitectureTest extends AbstractArchitectureTest {

    /**
     * Base test classes that are allowed to define Spring context configuration annotations.
     * These are the abstract base classes that define the test environments.
     */
    private static final Class<?>[] ALLOWED_BASE_CLASSES = {
            // Base class for all integration tests
            AbstractArtemisIntegrationTest.class,
            // Independent tests without CI/CD integration
            AbstractSpringIntegrationIndependentTest.class,
            // Jenkins + LocalVC integration tests
            AbstractSpringIntegrationJenkinsLocalVCTest.class, AbstractSpringIntegrationJenkinsLocalVCTestBase.class, AbstractSpringIntegrationJenkinsLocalVCTemplateTest.class,
            AbstractSpringIntegrationJenkinsLocalVCBatchTest.class,
            // Local CI + LocalVC integration tests
            AbstractSpringIntegrationLocalCILocalVCTest.class,
            // LocalVC with SAML authentication tests
            AbstractSpringIntegrationLocalVCSamlTest.class,
            // Build agent tests
            AbstractArtemisBuildAgentTest.class };

    /**
     * Specific test classes that are allowed exceptions for having their own configuration.
     * These are rare cases where a separate Spring context is justified.
     */
    private static final String[] ALLOWED_EXCEPTION_CLASSES = {
            // Redis-specific configuration requires a separate context
            "RedissonDistributedDataTest" };

    /**
     * Ensures that no test classes outside the allowed base classes use {@code @MockitoSpyBean}.
     * <p>
     * Using {@code @MockitoSpyBean} in concrete test classes causes Spring to create a new
     * application context, which increases server starts and test execution time.
     */
    @Test
    void testNoMockitoSpyBeanOutsideAllowedClasses() {
        var integrationTestClasses = testClasses.that(assignableTo(AbstractArtemisIntegrationTest.class)).that(not(belongToAnyOf(ALLOWED_BASE_CLASSES)));
        var filteredClasses = classesExcept(integrationTestClasses, ALLOWED_EXCEPTION_CLASSES);

        noFields().should().beAnnotatedWith(MockitoSpyBean.class)
                .because("@MockitoSpyBean should only be defined in allowed base test classes to prevent additional Spring context creation. "
                        + "See the developer documentation on 'Spring Boot Server Starts and Context Configuration' for details.")
                .check(filteredClasses);
    }

    /**
     * Ensures that no test classes outside the allowed base classes use {@code @MockitoBean}.
     * <p>
     * Using {@code @MockitoBean} in concrete test classes causes Spring to create a new
     * application context, which increases server starts and test execution time.
     */
    @Test
    void testNoMockitoBeanOutsideAllowedClasses() {
        var integrationTestClasses = testClasses.that(assignableTo(AbstractArtemisIntegrationTest.class)).that(not(belongToAnyOf(ALLOWED_BASE_CLASSES)));
        var filteredClasses = classesExcept(integrationTestClasses, ALLOWED_EXCEPTION_CLASSES);

        noFields().should().beAnnotatedWith(MockitoBean.class)
                .because("@MockitoBean should only be defined in allowed base test classes to prevent additional Spring context creation. "
                        + "See the developer documentation on 'Spring Boot Server Starts and Context Configuration' for details.")
                .check(filteredClasses);
    }

    /**
     * Ensures that no test classes outside the allowed base classes use {@code @TestPropertySource}.
     * <p>
     * Using {@code @TestPropertySource} with different properties causes Spring to create a new
     * application context. Properties should be added to the appropriate base class instead.
     */
    @Test
    void testNoTestPropertySourceOutsideAllowedClasses() {
        var integrationTestClasses = testClasses.that(assignableTo(AbstractArtemisIntegrationTest.class)).that(not(belongToAnyOf(ALLOWED_BASE_CLASSES)));
        var filteredClasses = classesExcept(integrationTestClasses, ALLOWED_EXCEPTION_CLASSES);

        noClasses().should().beAnnotatedWith(TestPropertySource.class)
                .because("@TestPropertySource should only be defined in allowed base test classes to prevent additional Spring context creation. "
                        + "Add the property to the appropriate base class instead. "
                        + "See the developer documentation on 'Spring Boot Server Starts and Context Configuration' for details.")
                .check(filteredClasses);
    }

    /**
     * Ensures that no test classes outside the allowed base classes use {@code @ActiveProfiles}.
     * <p>
     * Using {@code @ActiveProfiles} with different profiles causes Spring to create a new
     * application context. Profiles should be defined in the appropriate base class instead.
     */
    @Test
    void testNoActiveProfilesOutsideAllowedClasses() {
        var integrationTestClasses = testClasses.that(assignableTo(AbstractArtemisIntegrationTest.class)).that(not(belongToAnyOf(ALLOWED_BASE_CLASSES)));
        var filteredClasses = classesExcept(integrationTestClasses, ALLOWED_EXCEPTION_CLASSES);

        noClasses().should().beAnnotatedWith(ActiveProfiles.class)
                .because("@ActiveProfiles should only be defined in allowed base test classes to prevent additional Spring context creation. "
                        + "See the developer documentation on 'Spring Boot Server Starts and Context Configuration' for details.")
                .check(filteredClasses);
    }

    /**
     * Ensures that no test classes outside the allowed base classes use {@code @Profile}.
     * <p>
     * Note: {@code @Profile} on test classes is actually a no-op since test classes are not Spring beans.
     * However, we check for it to maintain consistency and avoid confusion.
     */
    @Test
    void testNoProfileOutsideAllowedClasses() {
        var integrationTestClasses = testClasses.that(assignableTo(AbstractArtemisIntegrationTest.class)).that(not(belongToAnyOf(ALLOWED_BASE_CLASSES)));
        var filteredClasses = classesExcept(integrationTestClasses, ALLOWED_EXCEPTION_CLASSES);

        noClasses().should().beAnnotatedWith(Profile.class)
                .because("@Profile should only be defined in allowed base test classes. " + "Note: @Profile on test classes is a no-op since test classes are not Spring beans. "
                        + "See the developer documentation on 'Spring Boot Server Starts and Context Configuration' for details.")
                .check(filteredClasses);
    }

    /**
     * Ensures that no test classes outside the allowed base classes use {@code @DirtiesContext}.
     * <p>
     * Using {@code @DirtiesContext} marks the Spring context as dirty, causing it to be recreated
     * for subsequent tests, which significantly increases test execution time.
     */
    @Test
    void testNoDirtiesContextOutsideAllowedClasses() {
        var integrationTestClasses = testClasses.that(assignableTo(AbstractArtemisIntegrationTest.class)).that(not(belongToAnyOf(ALLOWED_BASE_CLASSES)));
        var filteredClasses = classesExcept(integrationTestClasses, ALLOWED_EXCEPTION_CLASSES);

        noClasses().should().beAnnotatedWith(DirtiesContext.class)
                .because("@DirtiesContext should be avoided as it causes Spring context recreation. "
                        + "Instead, clean up test state manually or use appropriate test isolation techniques. "
                        + "See the developer documentation on 'Spring Boot Server Starts and Context Configuration' for details.")
                .check(filteredClasses);
    }

    /**
     * Ensures that no test classes outside the allowed base classes use {@code @ContextConfiguration}.
     * <p>
     * Using {@code @ContextConfiguration} with different configurations causes Spring to create
     * separate contexts, increasing server starts and test execution time.
     */
    @Test
    void testNoContextConfigurationOutsideAllowedClasses() {
        var integrationTestClasses = testClasses.that(assignableTo(AbstractArtemisIntegrationTest.class)).that(not(belongToAnyOf(ALLOWED_BASE_CLASSES)));
        var filteredClasses = classesExcept(integrationTestClasses, ALLOWED_EXCEPTION_CLASSES);

        noClasses().should().beAnnotatedWith(ContextConfiguration.class)
                .because("@ContextConfiguration should only be defined in allowed base test classes to prevent additional Spring context creation. "
                        + "See the developer documentation on 'Spring Boot Server Starts and Context Configuration' for details.")
                .check(filteredClasses);
    }

    /**
     * Ensures that no test classes outside the allowed base classes use {@code @SpringBootTest}.
     * <p>
     * The {@code @SpringBootTest} annotation should only be on base classes. Using it on concrete
     * test classes with different configurations causes Spring to create separate contexts.
     */
    @Test
    void testNoSpringBootTestOutsideAllowedClasses() {
        var integrationTestClasses = testClasses.that(assignableTo(AbstractArtemisIntegrationTest.class)).that(not(belongToAnyOf(ALLOWED_BASE_CLASSES)));
        var filteredClasses = classesExcept(integrationTestClasses, ALLOWED_EXCEPTION_CLASSES);

        noClasses().should().beAnnotatedWith(SpringBootTest.class)
                .because("@SpringBootTest should only be defined in allowed base test classes to prevent additional Spring context creation. "
                        + "See the developer documentation on 'Spring Boot Server Starts and Context Configuration' for details.")
                .check(filteredClasses);
    }

    /**
     * Ensures that no test classes outside the allowed base classes use {@code @Import}.
     * <p>
     * Using {@code @Import} to import different configuration classes causes Spring to create
     * separate contexts, increasing server starts and test execution time.
     */
    @Test
    void testNoImportOutsideAllowedClasses() {
        var integrationTestClasses = testClasses.that(assignableTo(AbstractArtemisIntegrationTest.class)).that(not(belongToAnyOf(ALLOWED_BASE_CLASSES)));
        var filteredClasses = classesExcept(integrationTestClasses, ALLOWED_EXCEPTION_CLASSES);

        noClasses().should().beAnnotatedWith(Import.class).because("@Import should only be defined in allowed base test classes to prevent additional Spring context creation. "
                + "See the developer documentation on 'Spring Boot Server Starts and Context Configuration' for details.").check(filteredClasses);
    }

    /**
     * Ensures that no test classes use {@code @Conditional*} annotations.
     * <p>
     * {@code @Conditional*} annotations are meant for Spring beans, not test classes.
     * Using them on test classes is a no-op and indicates a misunderstanding of their purpose.
     */
    @Test
    void testNoConditionalAnnotationsOnTestClasses() {
        var integrationTestClasses = testClasses.that(assignableTo(AbstractArtemisIntegrationTest.class)).that(not(belongToAnyOf(ALLOWED_BASE_CLASSES)));
        var filteredClasses = classesExcept(integrationTestClasses, ALLOWED_EXCEPTION_CLASSES);

        noClasses().should().beAnnotatedWith(Conditional.class).orShould().beAnnotatedWith(ConditionalOnProperty.class).orShould().beAnnotatedWith(ConditionalOnExpression.class)
                .orShould().beAnnotatedWith(ConditionalOnBean.class).orShould().beAnnotatedWith(ConditionalOnMissingBean.class).orShould().beAnnotatedWith(ConditionalOnClass.class)
                .orShould().beAnnotatedWith(ConditionalOnMissingClass.class)
                .because("@Conditional* annotations are meant for Spring beans, not test classes. " + "They have no effect on test classes and should not be used. "
                        + "See the developer documentation on 'Spring Boot Server Starts and Context Configuration' for details.")
                .check(filteredClasses);
    }
}
