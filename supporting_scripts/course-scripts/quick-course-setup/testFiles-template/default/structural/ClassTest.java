package ${packageName};

import java.net.URISyntaxException;

import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.TestFactory;

import de.tum.in.test.api.BlacklistPath;
import de.tum.in.test.api.PathType;
import de.tum.in.test.api.StrictTimeout;
import de.tum.in.test.api.WhitelistPath;
import de.tum.in.test.api.jupiter.Public;
import de.tum.in.test.api.structural.ClassTestProvider;

/**
 * @author Stephan Krusche (krusche@in.tum.de)
 * @version 5.1 (11.06.2021)
 * <br><br>
 * This test evaluates the hierarchy of the class, i.e. if the class is abstract or an interface or an enum and also if the class extends another superclass and if
 * it implements the interfaces and annotations, based on its definition in the structure oracle (test.json).
 */
@Public
@WhitelistPath("target") // mainly for Artemis
@BlacklistPath("target/test-classes") // prevent access to test-related classes and resources
class ClassTest extends ClassTestProvider {

    /**
     * This method collects the classes in the structure oracle file for which at least one class property is specified.
     * These classes are then transformed into JUnit 5 dynamic tests.
     * @return A dynamic test container containing the test for each class which is then executed by JUnit.
     */
    @Override
    @StrictTimeout(10)
    @TestFactory
    protected DynamicContainer generateTestsForAllClasses() throws URISyntaxException {
        structureOracleJSON = retrieveStructureOracleJSON(this.getClass().getResource("test.json"));
        return super.generateTestsForAllClasses();
    }
}
