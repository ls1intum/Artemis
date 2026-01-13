package de.tum.cit.aet.artemis.exam.architecture;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleEntityUsageArchitectureTest;

/**
 * Architecture test to verify that REST controllers in the Exam module
 * do not use @Entity types directly. Controllers should use DTOs instead.
 * <p>
 * Current violations:
 * <ul>
 * <li>Return types: 70</li>
 * <li>Request body/part inputs: 10</li>
 * </ul>
 */
class ExamEntityUsageArchitectureTest extends AbstractModuleEntityUsageArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".exam";
    }

    @Disabled("70 violations - controllers should return DTOs, not entities")
    @Test
    @Override
    protected void restControllersMustNotReturnEntities() {
        super.restControllersMustNotReturnEntities();
    }

    @Disabled("10 violations - controllers should accept DTOs, not entities")
    @Test
    @Override
    protected void restControllersMustNotAcceptEntitiesInRequestBodyOrPart() {
        super.restControllersMustNotAcceptEntitiesInRequestBodyOrPart();
    }
}
