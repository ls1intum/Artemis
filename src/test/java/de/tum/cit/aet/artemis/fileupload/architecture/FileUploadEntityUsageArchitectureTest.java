package de.tum.cit.aet.artemis.fileupload.architecture;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.shared.architecture.module.AbstractModuleEntityUsageArchitectureTest;

/**
 * Architecture test to verify that REST controllers in the FileUpload module
 * do not use @Entity types directly. Controllers should use DTOs instead.
 * <p>
 * Current violations:
 * <ul>
 * <li>Return types: 28</li>
 * <li>Request body/part inputs: 6</li>
 * </ul>
 */
class FileUploadEntityUsageArchitectureTest extends AbstractModuleEntityUsageArchitectureTest {

    @Override
    public String getModulePackage() {
        return ARTEMIS_PACKAGE + ".fileupload";
    }

    @Disabled("28 violations - controllers should return DTOs, not entities")
    @Test
    @Override
    protected void restControllersMustNotReturnEntities() {
        super.restControllersMustNotReturnEntities();
    }

    @Disabled("6 violations - controllers should accept DTOs, not entities")
    @Test
    @Override
    protected void restControllersMustNotAcceptEntitiesInRequestBodyOrPart() {
        super.restControllersMustNotAcceptEntitiesInRequestBodyOrPart();
    }
}
