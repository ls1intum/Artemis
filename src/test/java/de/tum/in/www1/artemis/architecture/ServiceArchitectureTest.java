package de.tum.in.www1.artemis.architecture;

import static com.tngtech.archunit.core.domain.JavaModifier.ABSTRACT;
import static com.tngtech.archunit.core.domain.JavaModifier.FINAL;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import com.tngtech.archunit.lang.ArchRule;

import de.tum.in.www1.artemis.config.migration.MigrationService;
import de.tum.in.www1.artemis.management.SecurityMetersService;
import de.tum.in.www1.artemis.security.DomainUserDetailsService;
import de.tum.in.www1.artemis.security.OAuth2JWKSService;
import de.tum.in.www1.artemis.security.jwt.JWTCookieService;
import de.tum.in.www1.artemis.web.rest.GitDiffReportParserService;
import de.tum.in.www1.artemis.web.websocket.ResultWebsocketService;
import de.tum.in.www1.artemis.web.websocket.localci.LocalCIWebsocketMessagingService;

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
        classes().that().resideInAPackage("de.tum.in.www1.artemis.service..").and().haveSimpleNameEndingWith("Service").and().areNotInterfaces().and().doNotHaveModifier(ABSTRACT)
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
}
