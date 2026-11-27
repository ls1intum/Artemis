package de.tum.cit.aet.artemis.sandbox;

import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTestBase;

/**
 * Base class of the integration tests of the sandbox module. The sandbox reads and changes the exercise-tests repository in LocalVC and triggers the
 * builds of LocalCI, so its tests run against both.
 */
public abstract class AbstractSandboxIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTestBase {
}
