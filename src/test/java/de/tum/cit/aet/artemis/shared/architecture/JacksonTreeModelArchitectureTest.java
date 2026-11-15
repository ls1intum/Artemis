package de.tum.cit.aet.artemis.shared.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Keeps the Jackson tree model out of the production code.
 * <p>
 * A {@code JsonNode} says nothing about what it holds: a misspelled field name, a number read as text and a missing field all compile
 * and fail, or silently produce a default, at run time. A record says what the JSON contains, is checked by the compiler, can be
 * documented and tested like any other DTO, and shows up in the API description. Production code therefore reads and writes JSON
 * through records, with {@code readValue} and {@code writeValueAsString}, and never through {@code JsonNode}, {@code ObjectNode},
 * {@code ArrayNode} or the other classes of the tree model.
 * <p>
 * There are no exceptions. JSON that arrives in a form no record can hold is read with the streaming API or an own deserializer into
 * a type that says what it means. Tests may use the tree model to inspect JSON; this rule looks at production code only.
 */
class JacksonTreeModelArchitectureTest extends AbstractArchitectureTest {

    private static final DescribedPredicate<JavaClass> JACKSON_TREE_TYPES = resideInAnyPackage("tools.jackson.databind.node..", "com.fasterxml.jackson.databind.node..")
            .or(nameMatching("(tools\\.jackson|com\\.fasterxml\\.jackson)\\.(databind\\.JsonNode|core\\.TreeNode)")).as("Jackson tree model types");

    private static final DescribedPredicate<JavaMethodCall> JACKSON_TREE_CALLS = new DescribedPredicate<>("calls that read or build a Jackson tree") {

        @Override
        public boolean test(JavaMethodCall call) {
            String owner = call.getTargetOwner().getName();
            boolean ownedByJackson = owner.startsWith("tools.jackson.") || owner.startsWith("com.fasterxml.jackson.");
            return ownedByJackson && call.getName().matches("readTree|valueToTree|treeToValue|createObjectNode|createArrayNode|nullNode|missingNode|readValueAsTree");
        }
    };

    @Test
    void productionCodeDoesNotUseTheJacksonTreeModel() {
        ArchRule rule = noClasses().should().dependOnClassesThat(JACKSON_TREE_TYPES)
                .because("JSON is read and written through records, which say what it contains " + "and are checked by the compiler. See the REST API guideline.");
        rule.check(productionClasses);
    }

    @Test
    void productionCodeDoesNotReadOrBuildJacksonTrees() {
        ArchRule rule = noClasses().should().callMethodWhere(JACKSON_TREE_CALLS)
                .because("a tree is only a JsonNode in disguise: use readValue into a record and writeValueAsString from a record.");
        rule.check(productionClasses);
    }
}
