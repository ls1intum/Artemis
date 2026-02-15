package de.tum.cit.aet.artemis.core.security.policy.docs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.policy.AccessPolicy;
import de.tum.cit.aet.artemis.core.security.policy.PolicyEffect;
import de.tum.cit.aet.artemis.core.security.policy.PolicyRule;
import de.tum.cit.aet.artemis.core.security.policy.definitions.CourseAccessPolicies;
import de.tum.cit.aet.artemis.core.security.policy.definitions.ProgrammingExerciseAccessPolicies;

/**
 * Standalone documentation generator that produces Markdown tables from access policy definitions.
 * <p>
 * Usage:
 *
 * <pre>
 * java PolicyDocGenerator path/to/access-rights.mdx           # generate/update
 * java PolicyDocGenerator path/to/access-rights.mdx --check   # check if up-to-date
 * </pre>
 */
public final class PolicyDocGenerator {

    /**
     * The columns in the generated table, in order.
     */
    static final List<Role> COLUMN_ORDER = List.of(Role.SUPER_ADMIN, Role.ADMIN, Role.INSTRUCTOR, Role.EDITOR, Role.TEACHING_ASSISTANT, Role.STUDENT);

    /**
     * Human-readable column headers for each role.
     */
    static final Map<Role, String> COLUMN_LABELS = Map.of(Role.SUPER_ADMIN, "Super Admin", Role.ADMIN, "Admin", Role.INSTRUCTOR, "Instructor", Role.EDITOR, "Editor",
            Role.TEACHING_ASSISTANT, "Teaching Assistant", Role.STUDENT, "Student");

    private PolicyDocGenerator() {
    }

    /**
     * Collects all access policies from known policy configuration classes.
     *
     * @return list of all access policies with documentation metadata
     */
    static List<AccessPolicy<?>> collectPolicies() {
        List<AccessPolicy<?>> policies = new ArrayList<>();
        CourseAccessPolicies courseConfig = new CourseAccessPolicies();
        policies.add(courseConfig.courseVisibilityPolicy());
        policies.add(courseConfig.courseStudentAccessPolicy());
        policies.add(courseConfig.courseStaffAccessPolicy());
        policies.add(courseConfig.courseEditorAccessPolicy());
        policies.add(courseConfig.courseInstructorAccessPolicy());
        ProgrammingExerciseAccessPolicies progConfig = new ProgrammingExerciseAccessPolicies();
        policies.add(progConfig.programmingExerciseVisibilityPolicy());
        return policies;
    }

    /**
     * A documentation row pairing a feature label with its policy.
     * One policy with multiple features expands into multiple rows.
     */
    record DocRow(String feature, AccessPolicy<?> policy) {
    }

    /**
     * Groups policies by their section name, expanding policies with multiple features
     * into one row per feature. Rows are sorted by feature name within each section.
     *
     * @param policies the policies to group
     * @return a map from section name to list of doc rows, preserving insertion order
     */
    static Map<String, List<DocRow>> groupBySection(List<AccessPolicy<?>> policies) {
        Map<String, List<DocRow>> sections = new LinkedHashMap<>();
        for (AccessPolicy<?> policy : policies) {
            String section = policy.getSection();
            if (section != null && !policy.getFeatures().isEmpty()) {
                List<DocRow> rows = sections.computeIfAbsent(section, _ -> new ArrayList<>());
                for (String feature : policy.getFeatures()) {
                    rows.add(new DocRow(feature, policy));
                }
            }
        }
        for (List<DocRow> list : sections.values()) {
            list.sort(Comparator.comparing(DocRow::feature));
        }
        return sections;
    }

    /**
     * Generates a markdown table for a list of doc rows in one section.
     *
     * @param docRows the doc rows (feature + policy pairs) in this section
     * @return the markdown table as a string (without trailing newline)
     */
    static String generateTable(List<DocRow> docRows) {
        // Compute column widths
        int featureWidth = "Feature".length();
        Map<Role, Integer> colWidths = new LinkedHashMap<>();
        for (Role role : COLUMN_ORDER) {
            colWidths.put(role, COLUMN_LABELS.get(role).length());
        }

        // Pre-compute cell values and track widths
        List<String[]> rows = new ArrayList<>();
        for (DocRow docRow : docRows) {
            String feature = docRow.feature();
            featureWidth = Math.max(featureWidth, feature.length());

            String[] cells = new String[COLUMN_ORDER.size()];
            Map<Role, String> roleNotes = buildRoleNotes(docRow.policy());

            for (int i = 0; i < COLUMN_ORDER.size(); i++) {
                Role role = COLUMN_ORDER.get(i);
                String cell = roleNotes.getOrDefault(role, "");
                cells[i] = cell;
                colWidths.put(role, Math.max(colWidths.get(role), cell.length()));
            }
            rows.add(new String[] { feature, String.join("\0", cells) });
        }

        // Build the table
        StringBuilder sb = new StringBuilder();

        // Header row
        sb.append("| ").append(pad("Feature", featureWidth)).append(" |");
        for (Role role : COLUMN_ORDER) {
            sb.append(" ").append(pad(COLUMN_LABELS.get(role), colWidths.get(role))).append(" |");
        }
        sb.append("\n");

        // Separator row
        sb.append("|").append("-".repeat(featureWidth + 2)).append("|");
        for (Role role : COLUMN_ORDER) {
            sb.append("-".repeat(colWidths.get(role) + 2)).append("|");
        }
        sb.append("\n");

        // Data rows
        for (String[] row : rows) {
            String feature = row[0];
            String[] cells = row[1].split("\0", -1);
            sb.append("| ").append(pad(feature, featureWidth)).append(" |");
            for (int i = 0; i < COLUMN_ORDER.size(); i++) {
                sb.append(" ").append(pad(cells[i], colWidths.get(COLUMN_ORDER.get(i)))).append(" |");
            }
            sb.append("\n");
        }

        // Remove trailing newline
        if (!sb.isEmpty() && sb.charAt(sb.length() - 1) == '\n') {
            sb.setLength(sb.length() - 1);
        }

        return sb.toString();
    }

    /**
     * Builds a map from role to cell text for a single policy.
     */
    private static Map<Role, String> buildRoleNotes(AccessPolicy<?> policy) {
        Map<Role, String> result = new LinkedHashMap<>();
        Set<Role> seen = EnumSet.noneOf(Role.class);

        for (PolicyRule<?> rule : policy.getRules()) {
            if (rule.effect() != PolicyEffect.ALLOW) {
                continue;
            }
            for (Role role : rule.documentedRoles()) {
                if (seen.add(role)) {
                    String note = rule.note();
                    result.put(role, note != null ? "\u2714 (" + note + ")" : "\u2714");
                }
            }
        }
        return result;
    }

    /**
     * Replaces the content between marker comments in the mdx content.
     *
     * @param mdxContent the original mdx file content
     * @param sections   map from section name to generated table content
     * @return the updated mdx content
     */
    static String replaceMarkers(String mdxContent, Map<String, String> sections) {
        String result = mdxContent;
        for (Map.Entry<String, String> entry : sections.entrySet()) {
            String sectionName = entry.getKey();
            String table = entry.getValue();
            String openMarker = "<!-- GENERATED:" + sectionName + " -->";
            String closeMarker = "<!-- /GENERATED:" + sectionName + " -->";
            Pattern pattern = Pattern.compile(Pattern.quote(openMarker) + ".*?" + Pattern.quote(closeMarker), Pattern.DOTALL);
            Matcher matcher = pattern.matcher(result);
            if (matcher.find()) {
                result = matcher.replaceFirst(Matcher.quoteReplacement(openMarker + "\n" + table + "\n" + closeMarker));
            }
        }
        return result;
    }

    private static String pad(String s, int width) {
        if (s.length() >= width) {
            return s;
        }
        return s + " ".repeat(width - s.length());
    }

    /**
     * Main entry point for the documentation generator.
     *
     * @param args [0] = path to access-rights.mdx, [1] = optional "--check" flag
     * @throws IOException if file I/O fails
     */
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: PolicyDocGenerator <path-to-access-rights.mdx> [--check]");
            System.exit(1);
        }

        Path mdxPath = Path.of(args[0]);
        boolean checkMode = args.length > 1 && "--check".equals(args[1]);

        String originalContent = Files.readString(mdxPath);

        List<AccessPolicy<?>> policies = collectPolicies();
        Map<String, List<DocRow>> grouped = groupBySection(policies);

        Map<String, String> tables = new LinkedHashMap<>();
        for (Map.Entry<String, List<DocRow>> entry : grouped.entrySet()) {
            tables.put(entry.getKey(), generateTable(entry.getValue()));
        }

        String updatedContent = replaceMarkers(originalContent, tables);

        if (checkMode) {
            if (!originalContent.equals(updatedContent)) {
                System.err.println("access-rights.mdx is out of date. Run './gradlew generateAccessDocs' to update.");
                System.exit(1);
            }
            else {
                System.out.println("access-rights.mdx is up to date.");
            }
        }
        else {
            Files.writeString(mdxPath, updatedContent);
            System.out.println("Updated " + mdxPath);
        }
    }
}
