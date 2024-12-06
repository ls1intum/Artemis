package de.tum.cit.aet.artemis.shared.architecture.module;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;

public abstract class AbstractModuleCodeStyleTest extends AbstractModuleAccessArchitectureTest {

    private static final Logger log = LoggerFactory.getLogger(AbstractModuleCodeStyleTest.class);

    /**
     * Threshold for number of classes with name ending DTO, that are no records or not annotated with JsonInclude.
     * We should aim to reduce this threshold.
     */
    protected abstract int dtoAsAnnotatedRecordThreshold();

    protected abstract int dtoNameEndingThreshold();

    @Test
    void testDTOImplementations() {
        var dtoRecordRule = classes().that().resideInAPackage(getModuleDtoSubpackage()).and().haveSimpleNameEndingWith("DTO").and().areNotInterfaces().should().beRecords()
                .andShould().beAnnotatedWith(JsonInclude.class).because("All DTOs should be records and annotated with @JsonInclude(JsonInclude.Include.NON_EMPTY)");
        var result = dtoRecordRule.evaluate(allClasses);
        log.info("Current number of DTO classes: {}", result.getFailureReport().getDetails().size());
        log.info("Current DTO classes: {}", result.getFailureReport().getDetails());
        // TODO: reduce the following number to 0, if the current number is less and the test fails, decrease it
        assertThat(result.getFailureReport().getDetails()).hasSize(dtoAsAnnotatedRecordThreshold());

        var dtoPackageRule = classes().that().resideInAPackage(getModuleDtoSubpackage()).should().haveSimpleNameEndingWith("DTO");
        result = dtoPackageRule.evaluate(allClasses);
        log.info("Current number of DTOs that do not end with \"DTO\": {}", result.getFailureReport().getDetails().size());
        log.info("Current DTOs that do not end with \"DTO\": {}", result.getFailureReport().getDetails());
        // TODO: reduce the following number to 0, if the current number is less and the test fails, decrease it
        assertThat(result.getFailureReport().getDetails()).hasSize(dtoNameEndingThreshold());
    }

}
