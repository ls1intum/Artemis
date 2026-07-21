package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The machine-readable grading plan the TESTS stage writes to {@code /workspace/test-plan.json}, implementing the specification's Testing Strategy with Artemis' native grading
 * features: per-test-case weights (core rules weigh more than edge cases) and {@code AFTER_DUE_DATE} visibility for overfit-resistant hidden variants.
 * <p>
 * Expected shape: <code>{"tests":[{"name":"&lt;exact test name&gt;","weight":1..3,"visibility":"ALWAYS"|"AFTER_DUE_DATE"}]}</code>. The TESTS stage gate validates every name
 * against the differential report's exact test names, so persistence can apply the plan without guessing; a plan that fails to parse at persist time is skipped, never fatal —
 * grading falls back to Artemis' defaults (weight 1, visible).
 */
public record GeneratedTestPlan(List<Entry> tests) {

    /** One test case's grading decision. */
    public record Entry(String name, double weight, String visibility) {
    }

    static final double MIN_WEIGHT = 1.0;

    static final double MAX_WEIGHT = 3.0;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Parses and validates the plan.
     *
     * @param json the raw {@code test-plan.json} content
     * @return the validated plan
     * @throws IllegalArgumentException with an actionable, agent-readable message when the JSON is malformed or an entry violates the schema
     */
    public static GeneratedTestPlan parse(String json) {
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        }
        catch (com.fasterxml.jackson.core.JacksonException e) {
            throw new IllegalArgumentException("test-plan.json is not valid JSON: " + e.getOriginalMessage());
        }
        JsonNode testsNode = root == null ? null : root.get("tests");
        if (testsNode == null || !testsNode.isArray() || testsNode.isEmpty()) {
            throw new IllegalArgumentException(
                    "test-plan.json must be {\"tests\":[{\"name\":...,\"weight\":1..3,\"visibility\":\"ALWAYS\"|\"AFTER_DUE_DATE\"}]} with at least " + "one entry.");
        }
        List<Entry> entries = new ArrayList<>();
        for (JsonNode testNode : testsNode) {
            String name = testNode.path("name").asText("").strip();
            if (name.isBlank()) {
                throw new IllegalArgumentException("Every test-plan.json entry needs a non-empty \"name\" (the exact test name verify reports).");
            }
            if (!testNode.path("weight").isNumber()) {
                throw new IllegalArgumentException("test-plan.json entry '" + name + "' needs a numeric \"weight\" between " + (int) MIN_WEIGHT + " and " + (int) MAX_WEIGHT
                        + " (core rules weigh more than edge cases).");
            }
            double weight = testNode.get("weight").asDouble();
            if (weight < MIN_WEIGHT || weight > MAX_WEIGHT) {
                throw new IllegalArgumentException(
                        "test-plan.json entry '" + name + "' has weight " + weight + "; weights must be between " + (int) MIN_WEIGHT + " and " + (int) MAX_WEIGHT + ".");
            }
            String visibility = testNode.path("visibility").asText("").strip();
            if (!"ALWAYS".equals(visibility) && !"AFTER_DUE_DATE".equals(visibility)) {
                throw new IllegalArgumentException("test-plan.json entry '" + name + "' has visibility '" + visibility + "'; use \"ALWAYS\" or \"AFTER_DUE_DATE\".");
            }
            entries.add(new Entry(name, weight, visibility));
        }
        List<String> duplicateNames = entries.stream().map(Entry::name).collect(java.util.stream.Collectors.groupingBy(name -> name)).entrySet().stream()
                .filter(group -> group.getValue().size() > 1).map(java.util.Map.Entry::getKey).sorted().toList();
        if (!duplicateNames.isEmpty()) {
            throw new IllegalArgumentException("test-plan.json lists these test names more than once: " + duplicateNames + ". Keep exactly one entry per test.");
        }
        return new GeneratedTestPlan(List.copyOf(entries));
    }

    /** The entries marked {@code AFTER_DUE_DATE}. */
    public List<Entry> hiddenEntries() {
        return tests.stream().filter(entry -> "AFTER_DUE_DATE".equals(entry.visibility())).toList();
    }
}
