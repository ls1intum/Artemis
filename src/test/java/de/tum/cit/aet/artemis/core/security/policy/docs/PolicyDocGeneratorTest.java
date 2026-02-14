package de.tum.cit.aet.artemis.core.security.policy.docs;

import static de.tum.cit.aet.artemis.core.security.Role.ADMIN;
import static de.tum.cit.aet.artemis.core.security.Role.EDITOR;
import static de.tum.cit.aet.artemis.core.security.Role.INSTRUCTOR;
import static de.tum.cit.aet.artemis.core.security.Role.STUDENT;
import static de.tum.cit.aet.artemis.core.security.Role.SUPER_ADMIN;
import static de.tum.cit.aet.artemis.core.security.Role.TEACHING_ASSISTANT;
import static de.tum.cit.aet.artemis.core.security.policy.AccessPolicy.when;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.security.policy.AccessPolicy;
import de.tum.cit.aet.artemis.core.security.policy.Conditions;
import de.tum.cit.aet.artemis.core.security.policy.PolicyCondition;

class PolicyDocGeneratorTest {

    private static final PolicyCondition<String> ALWAYS = Conditions.always();

    @Test
    void testGenerateTableSingleRow() {
        AccessPolicy<String> policy = AccessPolicy.forResource(String.class).named("test").section("TestSection").feature("Test Feature")
                .rule(when(ALWAYS).thenAllow().documentedFor(SUPER_ADMIN, ADMIN, INSTRUCTOR)).denyByDefault();

        String table = PolicyDocGenerator.generateTable(List.of(policy));

        assertThat(table).contains("| Test Feature");
        assertThat(table).contains("\u2714");
        assertThat(table).contains("Super Admin");
        assertThat(table).contains("Admin");
        assertThat(table).contains("Instructor");
    }

    @Test
    void testGenerateTableWithNote() {
        AccessPolicy<String> policy = AccessPolicy.forResource(String.class).named("test").section("TestSection").feature("My Feature")
                .rule(when(ALWAYS).thenAllow().documentedFor(ADMIN)).rule(when(ALWAYS).thenAllow().documentedFor(STUDENT).withNote("if enrolled")).denyByDefault();

        String table = PolicyDocGenerator.generateTable(List.of(policy));

        assertThat(table).contains("\u2714 (if enrolled)");
        assertThat(table).contains("| My Feature");
    }

    @Test
    void testGenerateTableMultipleRows() {
        AccessPolicy<String> policyA = AccessPolicy.forResource(String.class).named("a").section("S").feature("Alpha").rule(when(ALWAYS).thenAllow().documentedFor(ADMIN))
                .denyByDefault();

        AccessPolicy<String> policyB = AccessPolicy.forResource(String.class).named("b").section("S").feature("Beta")
                .rule(when(ALWAYS).thenAllow().documentedFor(ADMIN, INSTRUCTOR)).denyByDefault();

        String table = PolicyDocGenerator.generateTable(List.of(policyA, policyB));

        assertThat(table).contains("| Alpha");
        assertThat(table).contains("| Beta");
    }

    @Test
    void testGenerateTableAllColumns() {
        AccessPolicy<String> policy = AccessPolicy.forResource(String.class).named("test").section("S").feature("Full")
                .rule(when(ALWAYS).thenAllow().documentedFor(SUPER_ADMIN, ADMIN, INSTRUCTOR, EDITOR, TEACHING_ASSISTANT, STUDENT)).denyByDefault();

        String table = PolicyDocGenerator.generateTable(List.of(policy));

        for (String label : PolicyDocGenerator.COLUMN_LABELS.values()) {
            assertThat(table).contains(label);
        }
        String[] lines = table.split("\n");
        String dataRow = lines[2];
        long checkCount = dataRow.chars().filter(c -> c == '\u2714').count();
        assertThat(checkCount).isEqualTo(6);
    }

    @Test
    void testReplaceMarkers() {
        String mdx = """
                Some header content

                <!-- GENERATED:TestSection -->
                old table content
                <!-- /GENERATED:TestSection -->

                Some footer content
                """;

        String newTable = "| Feature | Admin |\n|---------|-------|\n| Foo     | \u2714     |";
        String result = PolicyDocGenerator.replaceMarkers(mdx, Map.of("TestSection", newTable));

        assertThat(result).contains("<!-- GENERATED:TestSection -->");
        assertThat(result).contains("<!-- /GENERATED:TestSection -->");
        assertThat(result).contains("| Foo     | \u2714     |");
        assertThat(result).doesNotContain("old table content");
        assertThat(result).contains("Some header content");
        assertThat(result).contains("Some footer content");
    }

    @Test
    void testReplaceMarkersPreservesUnmatchedSections() {
        String mdx = """
                <!-- GENERATED:Section1 -->
                old1
                <!-- /GENERATED:Section1 -->

                <!-- GENERATED:Section2 -->
                old2
                <!-- /GENERATED:Section2 -->
                """;

        String result = PolicyDocGenerator.replaceMarkers(mdx, Map.of("Section1", "new1"));

        assertThat(result).contains("new1");
        assertThat(result).contains("old2");
    }

    @Test
    void testCheckModeDetectsStaleness() {
        String mdx = """
                <!-- GENERATED:Nav -->
                stale content
                <!-- /GENERATED:Nav -->
                """;

        String updated = PolicyDocGenerator.replaceMarkers(mdx, Map.of("Nav", "fresh content"));

        assertThat(updated).isNotEqualTo(mdx);
    }

    @Test
    void testCheckModeUpToDate() {
        String table = "| Feature | Admin |\n|---------|-------|\n| Foo     | \u2714     |";
        String mdx = "<!-- GENERATED:Nav -->\n" + table + "\n<!-- /GENERATED:Nav -->\n";

        String updated = PolicyDocGenerator.replaceMarkers(mdx, Map.of("Nav", table));

        assertThat(updated).isEqualTo(mdx);
    }

    @Test
    void testGroupBySection() {
        AccessPolicy<String> p1 = AccessPolicy.forResource(String.class).named("a").section("Nav").feature("B").rule(when(ALWAYS).thenAllow()).denyByDefault();

        AccessPolicy<String> p2 = AccessPolicy.forResource(String.class).named("b").section("Nav").feature("A").rule(when(ALWAYS).thenAllow()).denyByDefault();

        AccessPolicy<String> p3 = AccessPolicy.forResource(String.class).named("c").section("Course").feature("X").rule(when(ALWAYS).thenAllow()).denyByDefault();

        Map<String, List<AccessPolicy<?>>> grouped = PolicyDocGenerator.groupBySection(List.of(p1, p2, p3));

        assertThat(grouped).containsKeys("Nav", "Course");
        assertThat(grouped.get("Nav")).hasSize(2);
        assertThat(grouped.get("Nav").get(0).getFeature()).isEqualTo("A");
        assertThat(grouped.get("Nav").get(1).getFeature()).isEqualTo("B");
    }

    @Test
    void testCollectPoliciesReturnsAtLeastCoursePolicy() {
        List<AccessPolicy<?>> policies = PolicyDocGenerator.collectPolicies();

        assertThat(policies).isNotEmpty();
        assertThat(policies).anyMatch(p -> "course-visibility".equals(p.getName()));
    }
}
