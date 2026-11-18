package de.tum.cit.aet.artemis.aiworker.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import de.tum.cit.aet.artemis.shared.architecture.AbstractArchitectureTest;

class AiWorkerAccessArchitectureTest extends AbstractArchitectureTest {

    private static final String MODULE = "de.tum.cit.aet.artemis.aiworker..";

    private static final String API = "de.tum.cit.aet.artemis.aiworker.api..";

    private static final String DTO = "de.tum.cit.aet.artemis.aiworker.dto..";

    private static final String DOMAIN = "de.tum.cit.aet.artemis.aiworker.domain..";

    @Test
    void otherModulesUseOnlyWorkerContracts() {
        ArchCondition<JavaClass> useOnlyContracts = new ArchCondition<>("use only AI Worker API, DTO and domain contracts") {

            @Override
            public void check(JavaClass origin, ConditionEvents events) {
                origin.getDirectDependenciesFromSelf().stream().map(dependency -> dependency.getTargetClass())
                        .filter(target -> resideInAPackage(MODULE).test(target) && !resideInAPackage(API).test(target) && !resideInAPackage(DTO).test(target)
                                && !resideInAPackage(DOMAIN).test(target))
                        .forEach(target -> events.add(SimpleConditionEvent.violated(origin, origin.getName() + " depends on internal AI Worker class " + target.getName())));
            }
        };
        classes().that().resideOutsideOfPackage(MODULE).should(useOnlyContracts).check(productionClasses);
    }
}
