package de.tum.cit.aet.artemis.communication.architecture;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleEntityUsageArchitectureTest;

/**
 * Architecture test to verify that REST controllers in the Communication module
 * do not use @Entity types directly. Controllers should use DTOs instead.
 * <p>
 * Current violations:
 * <ul>
 * <li>Return types: 40</li>
 * <li>Request body/part inputs: 8</li>
 * </ul>
 */
class CommunicationEntityUsageArchitectureTest extends AbstractModuleEntityUsageArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".communication";
    }

    @Disabled("40 violations - controllers should return DTOs, not entities")
    @Test
    @Override
    protected void restControllersMustNotReturnEntities() {
        super.restControllersMustNotReturnEntities();
    }

    @Disabled("8 violations - controllers should accept DTOs, not entities")
    @Test
    @Override
    protected void restControllersMustNotAcceptEntitiesInRequestBodyOrPart() {
        super.restControllersMustNotAcceptEntitiesInRequestBodyOrPart();
    }
}
