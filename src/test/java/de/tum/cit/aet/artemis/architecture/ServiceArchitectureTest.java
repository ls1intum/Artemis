package de.tum.cit.aet.artemis.architecture;

import static com.tngtech.archunit.core.domain.JavaModifier.ABSTRACT;
import static com.tngtech.archunit.core.domain.JavaModifier.FINAL;
import static com.tngtech.archunit.lang.SimpleConditionEvent.violated;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;

import de.tum.cit.aet.artemis.assessment.web.ResultWebsocketService;
import de.tum.cit.aet.artemis.core.config.migration.MigrationService;
import de.tum.cit.aet.artemis.core.management.SecurityMetersService;
import de.tum.cit.aet.artemis.core.security.DomainUserDetailsService;
import de.tum.cit.aet.artemis.core.security.jwt.JWTCookieService;
import de.tum.cit.aet.artemis.lti.service.OAuth2JWKSService;
import de.tum.cit.aet.artemis.programming.service.localci.LocalCIWebsocketMessagingService;
import de.tum.cit.aet.artemis.programming.web.GitDiffReportParserService;

class ServiceArchitectureTest extends AbstractArchitectureTest {

    @Test
    void shouldBeNamedService() {
        ArchRule rule = classes().that().areAnnotatedWith(Service.class).should().haveSimpleNameEndingWith("Service").because("services should have a name ending with 'Service'.");
        rule.check(productionClasses);
    }

    @Test
    void shouldBeInServicePackage() {
        ArchRule rule = classes().that().areAnnotatedWith(Service.class).should().resideInAPackage("..service..").because("services should be in the package 'service'.");
        final var exceptions = new Class[] { MigrationService.class, SecurityMetersService.class, DomainUserDetailsService.class, OAuth2JWKSService.class, JWTCookieService.class,
                GitDiffReportParserService.class, ResultWebsocketService.class, LocalCIWebsocketMessagingService.class };
        final var classes = classesExcept(productionClasses, exceptions);
        rule.check(classes);
    }

    @Test
    void testNoWrongServiceImports() {
        ArchRule rule = noClasses().should().dependOnClassesThat().resideInAnyPackage("org.jvnet.hk2.annotations")
                .because("this is the wrong service class, use org.springframework.stereotype.Service.");
        rule.check(allClasses);
    }

    @Test
    void testCorrectServiceAnnotation() {
        classes().that().resideInAPackage("de.tum.cit.aet.artemis.service..").and().haveSimpleNameEndingWith("Service").and().areNotInterfaces().and().doNotHaveModifier(ABSTRACT)
                .should().beAnnotatedWith(org.springframework.stereotype.Service.class)
                .because("services should be consistently managed by Spring's dependency injection container.").check(allClasses);

        classes().that().haveSimpleNameEndingWith("Service").should().notBeAnnotatedWith(Component.class).check(allClasses);
        classes().that().haveSimpleNameEndingWith("Service").should().notBeAnnotatedWith(RestController.class).check(allClasses);
        classes().that().haveSimpleNameEndingWith("Service").should().notHaveModifier(FINAL).check(allClasses);

        classes().that().areAnnotatedWith(Service.class).should().haveSimpleNameEndingWith("Service").check(allClasses);
        classes().that().areAnnotatedWith(Service.class).should().notBeAnnotatedWith(Component.class).check(allClasses);
        classes().that().areAnnotatedWith(Service.class).should().notBeAnnotatedWith(RestController.class).check(allClasses);
        classes().that().areAnnotatedWith(Service.class).should().notHaveModifier(FINAL).check(allClasses);
    }

    @Test
    void testCorrectAsyncCalls() {
        var noCallsFromOwnClass = methods().that().areAnnotatedWith(Async.class).should(new ArchCondition<>("not be called within the same class") {

            @Override
            public void check(JavaMethod javaMethod, ConditionEvents conditionEvents) {
                var declaredInClass = javaMethod.getOwner();
                // Async methods should not be called from the same class, except if the caller is also Async
                javaMethod.getCallsOfSelf().stream().filter(call -> call.getOriginOwner().equals(declaredInClass) && !call.getOwner().isAnnotatedWith(Async.class))
                        .forEach(call -> conditionEvents.add(
                                violated(call, "Method %s should only be called from the outside method %s.".formatted(javaMethod.getFullName(), call.getSourceCodeLocation()))));
                // Async methods should not be called from other Async methods from other classes (double thread creation)
                javaMethod.getCallsOfSelf().stream().filter(call -> !call.getOriginOwner().equals(declaredInClass) && call.getOwner().isAnnotatedWith(Async.class))
                        .forEach(call -> conditionEvents.add(
                                violated(call, "Method %s should not be called from another Async method %s".formatted(javaMethod.getFullName(), call.getSourceCodeLocation()))));
            }
        }).because("Methods annotated with @Async are meant to be executed in a new thread."
                + " The thread gets created in a Spring proxy subclass and requires the method to only be called from the outside.");
        noCallsFromOwnClass.check(productionClasses);
    }
}
