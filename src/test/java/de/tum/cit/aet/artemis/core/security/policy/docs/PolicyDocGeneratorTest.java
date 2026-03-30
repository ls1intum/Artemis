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

        String table = PolicyDocGenerator.generateTable(List.of(new PolicyDocGenerator.DocRow("Test Feature", policy)));

        assertThat(table).contains("| Test Feature");
        assertThat(table).contains("\u2714");
        // Super Admin and Admin should be removed when Instructor is present
        assertThat(table).doesNotContain("Super Admin");
        assertThat(table).doesNotContain("Admin");
        assertThat(table).contains("Instructor");
    }

    @Test
    void testGenerateTableWithNote() {
        AccessPolicy<String> policy = AccessPolicy.forResource(String.class).named("test").section("TestSection").feature("My Feature")
                .rule(when(ALWAYS).thenAllow().documentedFor(ADMIN)).rule(when(ALWAYS).thenAllow().documentedFor(STUDENT).withNote("if enrolled")).denyByDefault();

        String table = PolicyDocGenerator.generateTable(List.of(new PolicyDocGenerator.DocRow("My Feature", policy)));

        assertThat(table).contains("\u2714 (if enrolled)");
        assertThat(table).contains("| My Feature");
    }

    @Test
    void testGenerateTableMultipleRows() {
        AccessPolicy<String> policyA = AccessPolicy.forResource(String.class).named("a").section("S").feature("Alpha").rule(when(ALWAYS).thenAllow().documentedFor(ADMIN))
                .denyByDefault();

        AccessPolicy<String> policyB = AccessPolicy.forResource(String.class).named("b").section("S").feature("Beta")
                .rule(when(ALWAYS).thenAllow().documentedFor(ADMIN, INSTRUCTOR)).denyByDefault();

        String table = PolicyDocGenerator.generateTable(List.of(new PolicyDocGenerator.DocRow("Alpha", policyA), new PolicyDocGenerator.DocRow("Beta", policyB)));

        assertThat(table).contains("| Alpha");
        assertThat(table).contains("| Beta");
    }

    @Test
    void testGenerateTableAllColumns() {
        AccessPolicy<String> policy = AccessPolicy.forResource(String.class).named("test").section("S").feature("Full")
                .rule(when(ALWAYS).thenAllow().documentedFor(SUPER_ADMIN, ADMIN, INSTRUCTOR, EDITOR, TEACHING_ASSISTANT, STUDENT)).denyByDefault();

        String table = PolicyDocGenerator.generateTable(List.of(new PolicyDocGenerator.DocRow("Full", policy)));

        // Super Admin and Admin should be removed when course-specific roles are present
        assertThat(table).doesNotContain("Super Admin");
        assertThat(table).doesNotContain("Admin");
        // All course-specific roles should be present
        assertThat(table).contains("Instructor");
        assertThat(table).contains("Editor");
        assertThat(table).contains("Teaching Assistant");
        assertThat(table).contains("Student");

        String[] lines = table.split("\n");
        String dataRow = lines[2];
        long checkCount = dataRow.chars().filter(c -> c == '\u2714').count();
        // Should have 4 checkmarks (Instructor, Editor, Teaching Assistant, Student)
        assertThat(checkCount).isEqualTo(4);
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

        Map<String, List<PolicyDocGenerator.DocRow>> grouped = PolicyDocGenerator.groupBySection(List.of(p1, p2, p3));

        assertThat(grouped).containsKeys("Nav", "Course");
        assertThat(grouped.get("Nav")).hasSize(2);
        assertThat(grouped.get("Nav").get(0).feature()).isEqualTo("A");
        assertThat(grouped.get("Nav").get(1).feature()).isEqualTo("B");
    }

    @Test
    void testGroupBySectionExpandsMultipleFeatures() {
        AccessPolicy<String> multiFeature = AccessPolicy.forResource(String.class).named("multi").section("Nav").features("Feature A", "Feature B", "Feature C")
                .rule(when(ALWAYS).thenAllow().documentedFor(ADMIN)).denyByDefault();

        Map<String, List<PolicyDocGenerator.DocRow>> grouped = PolicyDocGenerator.groupBySection(List.of(multiFeature));

        assertThat(grouped.get("Nav")).hasSize(3);
        assertThat(grouped.get("Nav").get(0).feature()).isEqualTo("Feature A");
        assertThat(grouped.get("Nav").get(1).feature()).isEqualTo("Feature B");
        assertThat(grouped.get("Nav").get(2).feature()).isEqualTo("Feature C");
        // All rows share the same policy
        assertThat(grouped.get("Nav")).allMatch(row -> row.policy() == multiFeature);
    }

    @Test
    void testCollectPoliciesReturnsAtLeastCoursePolicy() {
        List<AccessPolicy<?>> policies = PolicyDocGenerator.collectPolicies();

        assertThat(policies).isNotEmpty();
        assertThat(policies).anyMatch(p -> "course-visibility".equals(p.getName()));
    }

    @Test
    void testCollectPoliciesReturnsProgrammingExercisePolicy() {
        List<AccessPolicy<?>> policies = PolicyDocGenerator.collectPolicies();

        assertThat(policies).anyMatch(p -> "programming-exercise-visibility".equals(p.getName()));
    }

    @Test
    void testRemoveRedundantAdminRoles_RemovesAdminWhenInstructorPresent() {
        // Policy with Super Admin, Admin, and Instructor
        AccessPolicy<String> policy = AccessPolicy.forResource(String.class).named("test").section("S").feature("Feature")
                .rule(when(ALWAYS).thenAllow().documentedFor(SUPER_ADMIN, ADMIN, INSTRUCTOR)).denyByDefault();

        String table = PolicyDocGenerator.generateTable(List.of(new PolicyDocGenerator.DocRow("Feature", policy)));

        // Should only show Instructor, not Super Admin or Admin
        assertThat(table).contains("Instructor");
        assertThat(table).doesNotContain("Super Admin");
        assertThat(table).doesNotContain("Admin");
    }

    @Test
    void testRemoveRedundantAdminRoles_PreservesAllCourseRoles() {
        // Policy with Super Admin, Admin, Instructor, Editor, and Teaching Assistant (staff access)
        AccessPolicy<String> policy = AccessPolicy.forResource(String.class).named("test").section("S").feature("Staff Feature")
                .rule(when(ALWAYS).thenAllow().documentedFor(SUPER_ADMIN, ADMIN, INSTRUCTOR, EDITOR, TEACHING_ASSISTANT)).denyByDefault();

        String table = PolicyDocGenerator.generateTable(List.of(new PolicyDocGenerator.DocRow("Staff Feature", policy)));

        // Should show all course-specific roles
        assertThat(table).contains("Instructor");
        assertThat(table).contains("Editor");
        assertThat(table).contains("Teaching Assistant");
        // Should not show admin roles
        assertThat(table).doesNotContain("Super Admin");
        assertThat(table).doesNotContain("Admin");
    }

    @Test
    void testRemoveRedundantAdminRoles_KeepsAdminWhenNoCourseRoles() {
        // Policy with only Super Admin and Admin (no course-specific roles)
        AccessPolicy<String> policy = AccessPolicy.forResource(String.class).named("test").section("S").feature("Admin Only")
                .rule(when(ALWAYS).thenAllow().documentedFor(SUPER_ADMIN, ADMIN)).denyByDefault();

        String table = PolicyDocGenerator.generateTable(List.of(new PolicyDocGenerator.DocRow("Admin Only", policy)));

        // Should show both admin roles since no course-specific roles are present
        assertThat(table).contains("Super Admin");
        assertThat(table).contains("Admin");
    }

    @Test
    void testRemoveRedundantAdminRoles_PreservesStudentRole() {
        // Policy with all roles including Student
        AccessPolicy<String> policy = AccessPolicy.forResource(String.class).named("test").section("S").feature("All Access")
                .rule(when(ALWAYS).thenAllow().documentedFor(SUPER_ADMIN, ADMIN, INSTRUCTOR, EDITOR, TEACHING_ASSISTANT, STUDENT)).denyByDefault();

        String table = PolicyDocGenerator.generateTable(List.of(new PolicyDocGenerator.DocRow("All Access", policy)));

        // Should show all course-specific roles including Student
        assertThat(table).contains("Student");
        assertThat(table).contains("Instructor");
        assertThat(table).contains("Editor");
        assertThat(table).contains("Teaching Assistant");
        // Should not show admin roles
        assertThat(table).doesNotContain("Super Admin");
        assertThat(table).doesNotContain("Admin");

        // Count checkmarks
        String[] lines = table.split("\n");
        String dataRow = lines[2];
        long checkCount = dataRow.chars().filter(c -> c == '\u2714').count();
        assertThat(checkCount).isEqualTo(4); // Instructor, Editor, Teaching Assistant, Student
    }

    @Test
    void testRemoveRedundantAdminRoles_StudentOnlyDoesNotShowAdminWhenSameCondition() {
        // Policy where Admin and Student have the SAME condition - Admin should be removed
        AccessPolicy<String> policy = AccessPolicy.forResource(String.class).named("test").section("S").feature("Student Feature")
                .rule(when(ALWAYS).thenAllow().documentedFor(SUPER_ADMIN, ADMIN, STUDENT)).denyByDefault();

        String table = PolicyDocGenerator.generateTable(List.of(new PolicyDocGenerator.DocRow("Student Feature", policy)));

        // Should only show Student since all have same condition
        assertThat(table).contains("Student");
        assertThat(table).doesNotContain("Super Admin");
        assertThat(table).doesNotContain("Admin");
    }

    @Test
    void testRemoveRedundantAdminRoles_StudentWithDifferentConditionKeepsAdmin() {
        // Policy where Admin has unconditional and Student has conditional - both should show
        AccessPolicy<String> policy = AccessPolicy.forResource(String.class).named("test").section("S").feature("Student Feature")
                .rule(when(ALWAYS).thenAllow().documentedFor(SUPER_ADMIN, ADMIN)).rule(when(ALWAYS).thenAllow().documentedFor(STUDENT).withNote("if enrolled")).denyByDefault();

        String table = PolicyDocGenerator.generateTable(List.of(new PolicyDocGenerator.DocRow("Student Feature", policy)));

        // Should show both Admin (unconditional) and Student (conditional)
        assertThat(table).contains("Admin");
        assertThat(table).contains("Super Admin");
        assertThat(table).contains("Student");
        assertThat(table).contains("\u2714 (if enrolled)");
    }

    @Test
    void testRemoveRedundantAdminRoles_EditorOnlyDoesNotShowAdmin() {
        // Policy with Super Admin, Admin, Instructor, and Editor
        AccessPolicy<String> policy = AccessPolicy.forResource(String.class).named("test").section("S").feature("Editor Feature")
                .rule(when(ALWAYS).thenAllow().documentedFor(SUPER_ADMIN, ADMIN, INSTRUCTOR, EDITOR)).denyByDefault();

        String table = PolicyDocGenerator.generateTable(List.of(new PolicyDocGenerator.DocRow("Editor Feature", policy)));

        // Should show course-specific roles only
        assertThat(table).contains("Instructor");
        assertThat(table).contains("Editor");
        assertThat(table).doesNotContain("Super Admin");
        assertThat(table).doesNotContain("Admin");

        // Count checkmarks
        String[] lines = table.split("\n");
        String dataRow = lines[2];
        long checkCount = dataRow.chars().filter(c -> c == '\u2714').count();
        assertThat(checkCount).isEqualTo(2); // Instructor and Editor
    }

    @Test
    void testRemoveRedundantAdminRoles_DifferentConditionsPreservesAdmin() {
        // Policy with different conditions: Admin has unconditional access, course roles have conditional access
        AccessPolicy<String> policy = AccessPolicy.forResource(String.class).named("test").section("S").feature("Programming Exercise")
                .rule(when(ALWAYS).thenAllow().documentedFor(SUPER_ADMIN, ADMIN))
                .rule(when(ALWAYS).thenAllow().documentedFor(INSTRUCTOR, EDITOR, TEACHING_ASSISTANT).withNote("if in course")).denyByDefault();

        String table = PolicyDocGenerator.generateTable(List.of(new PolicyDocGenerator.DocRow("Programming Exercise", policy)));

        // Should show Admin (unconditional) AND course roles (conditional)
        assertThat(table).contains("Super Admin");
        assertThat(table).contains("Admin");
        assertThat(table).contains("Instructor");
        assertThat(table).contains("Editor");
        assertThat(table).contains("Teaching Assistant");

        // Verify the different conditions are preserved
        assertThat(table).contains("\u2714 (if in course)");

        // Count checkmarks
        String[] lines = table.split("\n");
        String dataRow = lines[2];
        // Should have 5 checkmarks: Super Admin, Admin (no condition), Instructor, Editor, TA (with condition)
        long checkCount = dataRow.chars().filter(c -> c == '\u2714').count();
        assertThat(checkCount).isEqualTo(5);
    }

    @Test
    void testRemoveRedundantAdminRoles_MixedConditionsInMultipleRules() {
        // Complex case: Admin in one rule (no condition), Student in another (with condition)
        AccessPolicy<String> policy = AccessPolicy.forResource(String.class).named("test").section("S").feature("View Feature").rule(when(ALWAYS).thenAllow().documentedFor(ADMIN))
                .rule(when(ALWAYS).thenAllow().documentedFor(STUDENT).withNote("if enrolled")).denyByDefault();

        String table = PolicyDocGenerator.generateTable(List.of(new PolicyDocGenerator.DocRow("View Feature", policy)));

        // Should show both Admin (unconditional) and Student (conditional)
        assertThat(table).doesNotContain("Super Admin"); // Super Admin not in policy
        assertThat(table).contains("Admin");
        assertThat(table).contains("Student");
        assertThat(table).contains("\u2714 (if enrolled)");
    }
}
