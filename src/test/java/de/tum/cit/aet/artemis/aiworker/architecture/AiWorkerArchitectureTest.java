package de.tum.cit.aet.artemis.aiworker.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import de.tum.cit.aet.artemis.core.config.Constants;

/** Applies feature-package and service conventions to the AI Worker code in the Artemis application. */
@Tag("ArchitectureTest")
class AiWorkerArchitectureTest {

    private static final JavaClasses CLASSES = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("de.tum.cit.aet.artemis.aiworker");

    @Test
    void reusableInfrastructureCannotDependOnAWorkload() {
        noClasses().that().resideInAPackage("..aiworker..").should().dependOnClassesThat()
                .resideInAnyPackage("de.tum.cit.aet.artemis.hyperion..", "de.tum.cit.aet.artemis.programming..", "de.tum.cit.aet.artemis.exercise..",
                        "de.tum.cit.aet.artemis.iris..", "de.tum.cit.aet.artemis.athena..", "de.tum.cit.aet.artemis.localci..", "de.tum.cit.aet.artemis.localvc..")
                .check(CLASSES);
    }

    @Test
    void workerCannotAccessDatabaseOrClusterProviders() {
        noClasses().that().resideInAnyPackage("..aiworker.service.sandbox..", "..aiworker.service.messaging..").should().dependOnClassesThat()
                .resideInAnyPackage("java.sql..", "javax.sql..", "jakarta.persistence..", "org.springframework.data..", "org.hibernate..", "com.hazelcast..", "org.redisson..",
                        "org.springframework.jdbc..", "jakarta.servlet..", "org.springframework.web.servlet..", "org.springframework.web.socket..")
                .check(CLASSES);
    }

    @Test
    void springConfigurationsStayInTheConfigPackage() {
        classes().that().areAnnotatedWith(Configuration.class).should().resideInAPackage("..config..").check(CLASSES);
    }

    @Test
    void mvcComponentsInTheWorkerScanAreCoreOnly() {
        var controllers = CLASSES.stream().filter(type -> type.isAnnotatedWith(Controller.class) || type.isAnnotatedWith(RestController.class)).toList();
        assertThat(controllers).isNotEmpty();
        for (var controller : controllers) {
            assertThat(controller.isAnnotatedWith(Profile.class)).as(controller.getName()).isTrue();
            assertThat(controller.getAnnotationOfType(Profile.class).value()).as(controller.getName()).containsExactly(Constants.PROFILE_CORE);
        }
    }

    @Test
    void importedClassesIncludeTheWorkerImplementation() {
        assertThat(CLASSES.stream().map(type -> type.getName())).contains("de.tum.cit.aet.artemis.aiworker.service.sandbox.DockerSandboxService",
                "de.tum.cit.aet.artemis.aiworker.api.SandboxApi", "de.tum.cit.aet.artemis.aiworker.service.WorkerSupervisorService");
    }
}
