package de.tum.cit.aet.artemis.account.architecture;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleEntityUsageArchitectureTest;

/**
 * Architecture test verifying that REST controllers in the Account module do not use @Entity types
 * directly (neither as return types nor in request bodies) and that DTOs do not reference entities.
 * Controllers should use DTOs instead.
 */
class AccountEntityUsageArchitectureTest extends AbstractModuleEntityUsageArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".account";
    }

    // TODO: Reduce this to 0 by returning DTOs instead of entities (OrganizationResource#getAllOrganizationsByCourse)
    @Override
    protected int getExpectedEntityReturnViolations() {
        return 1;
    }

    @Override
    protected int getExpectedEntityInputViolations() {
        return 0;
    }
}
