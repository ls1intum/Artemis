package de.tum.cit.aet.artemis.text.architecture;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleEntityUsageArchitectureTest;

/**
 * Architecture test to verify that REST controllers in the Text module
 * do not use @Entity types directly. Controllers should use DTOs instead.
 * <p>
 * Current violations:
 * <ul>
 * <li>Return types: 30</li>
 * <li>Request body/part inputs: 10</li>
 * </ul>
 */
class TextEntityUsageArchitectureTest extends AbstractModuleEntityUsageArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".text";
    }

    @Disabled("30 violations - controllers should return DTOs, not entities")
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
