package ${packageName};

import java.net.URISyntaxException;

import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Timeout;

import de.tum.in.test.api.structural.ConstructorTest;

/**
 * @author Stephan Krusche (krusche@in.tum.de)
 * @version 5.0 (11.11.2020)
 * <br><br>
 * This test evaluates if the specified constructors in the structure oracle are correctly implemented with the expected parameter types and annotations,
 * based on its definition in the structure oracle (test.json).
 */
public class LocalConstructorTest extends ConstructorTest {

    /**
     * This method collects the classes in the structure oracle file for which constructors are specified.
     * These classes are then transformed into JUnit 5 dynamic tests.
     * @return A dynamic test container containing the test for each class which is then executed by JUnit.
     */
    @Timeout(10)
    @TestFactory
    public DynamicContainer generateTestsForAllClasses() throws URISyntaxException {
        structureOracleJSON = retrieveStructureOracleJSON(this.getClass().getResource("test.json"));
        return super.generateTestsForAllClasses();
    }
}
