package de.tum.cit.aet.artemis.modeling.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleEntityUsageArchitectureTest;

/**
 * Architecture test to verify that REST controllers in the Modeling module
 * do not use @Entity types directly. Controllers should use DTOs instead.
 * <p>
 * TODO: Reduce violation counts to 0 by introducing DTOs for all endpoints.
 */
class ModelingEntityUsageArchitectureTest extends AbstractModuleEntityUsageArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".modeling";
    }

    // TODO: Reduce this to 0 by returning DTOs instead of entities
    @Override
    protected int getMaxEntityReturnViolations() {
        return 25;
    }

    // TODO: Reduce this to 0 by accepting DTOs instead of entities in @RequestBody/@RequestPart
    @Override
    protected int getMaxEntityInputViolations() {
        return 7;
    }

    // TODO: Reduce this to 0 by removing entity references from DTOs
    @Override
    protected int getMaxDtoEntityFieldViolations() {
        return 1;
    }
}
