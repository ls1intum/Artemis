package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The machine-readable grading plan the TESTS stage writes to {@code /workspace/test-plan.json}, implementing the specification's Testing Strategy with Artemis' native grading
 * features: seam-level importance tiers and {@code AFTER_DUE_DATE} visibility for overfit-resistant hidden variants.
 * <p>
 * A tier belongs to a seam rather than to a single test and is repeated on each of that seam's tests so entries stay self-contained; Artemis stores weights per test case, so the
 * tier is divided evenly across the seam's cases. The seam id itself is transient generation metadata that persistence ignores, and a plan without seams still parses so a
 * candidate verified without them can still be persisted; the TESTS-stage gate is what requires seams wherever the specification declares them. Risk-partition IDs trace the
 * specification's boundary inventory to executable evidence and are likewise generation metadata.
 */
public record GeneratedTestPlan(List<Entry> tests) {

    public record Entry(String name, String seam, double seamWeightTier, String visibility, List<String> riskPartitions) {

        public Entry {
            riskPartitions = riskPartitions == null ? List.of() : List.copyOf(riskPartitions);
        }

        public Entry(String name, String seam, double seamWeightTier, String visibility) {
            this(name, seam, seamWeightTier, visibility, List.of());
        }
    }

    private static final double MIN_SEAM_WEIGHT_TIER = 1.0;

    private static final double MAX_SEAM_WEIGHT_TIER = 3.0;

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
            throw new IllegalArgumentException("test-plan.json must be {\"tests\":[{\"name\":...,\"seamWeightTier\":1..3,\"visibility\":\"ALWAYS\"|\"AFTER_DUE_DATE\","
                    + "\"riskPartitions\":[\"S1.P1\"]}]} with at least one entry.");
        }
        List<Entry> entries = new ArrayList<>();
        for (JsonNode testNode : testsNode) {
            String name = testNode.path("name").asText("").strip();
            if (name.isBlank()) {
                throw new IllegalArgumentException("Every test-plan.json entry needs a non-empty \"name\" (the exact test name verify reports).");
            }
            String seam = testNode.path("seam").asText("").strip();
            if (!seam.isEmpty() && !seam.matches("S[1-9][0-9]*")) {
                throw new IllegalArgumentException("test-plan.json entry '" + name + "' has seam '" + seam + "'; use a stable SPEC seam ID such as \"S1\".");
            }
            // "weight" is accepted as a read-only alias, but never echoed back: the field carries a seam tier, not the per-case weight persistence derives from it.
            JsonNode tierNode = testNode.has("seamWeightTier") ? testNode.get("seamWeightTier") : testNode.get("weight");
            if (tierNode == null || !tierNode.isNumber()) {
                throw new IllegalArgumentException("test-plan.json entry '" + name + "' needs a numeric \"seamWeightTier\" between " + (int) MIN_SEAM_WEIGHT_TIER + " and "
                        + (int) MAX_SEAM_WEIGHT_TIER + " (core seams weigh more than supporting or edge seams).");
            }
            double tier = tierNode.asDouble();
            if (tier < MIN_SEAM_WEIGHT_TIER || tier > MAX_SEAM_WEIGHT_TIER) {
                throw new IllegalArgumentException("test-plan.json entry '" + name + "' has seamWeightTier " + tier + "; tiers must be between " + (int) MIN_SEAM_WEIGHT_TIER
                        + " and " + (int) MAX_SEAM_WEIGHT_TIER + ".");
            }
            String visibility = testNode.path("visibility").asText("").strip();
            if (!"ALWAYS".equals(visibility) && !"AFTER_DUE_DATE".equals(visibility)) {
                throw new IllegalArgumentException("test-plan.json entry '" + name + "' has visibility '" + visibility + "'; use \"ALWAYS\" or \"AFTER_DUE_DATE\".");
            }
            List<String> riskPartitions = parseRiskPartitions(testNode, name, seam);
            entries.add(new Entry(name, seam, tier, visibility, riskPartitions));
        }
        List<String> duplicateNames = entries.stream().map(Entry::name).collect(Collectors.groupingBy(name -> name)).entrySet().stream()
                .filter(group -> group.getValue().size() > 1).map(Map.Entry::getKey).sorted().toList();
        if (!duplicateNames.isEmpty()) {
            throw new IllegalArgumentException("test-plan.json lists these test names more than once: " + duplicateNames + ". Keep exactly one entry per test.");
        }
        return new GeneratedTestPlan(List.copyOf(entries));
    }

    private static List<String> parseRiskPartitions(JsonNode testNode, String name, String seam) {
        JsonNode partitionsNode = testNode.get("riskPartitions");
        if (partitionsNode == null) {
            return List.of();
        }
        if (!partitionsNode.isArray()) {
            throw new IllegalArgumentException("test-plan.json entry '" + name + "' needs \"riskPartitions\" as an array of stable SPEC IDs such as [\"S1.P1\"].");
        }
        List<String> partitions = new ArrayList<>();
        for (JsonNode partitionNode : partitionsNode) {
            String partition = partitionNode.isTextual() ? partitionNode.asText().strip() : "";
            if (!partition.matches("S[1-9][0-9]*\\.P[1-9][0-9]*")) {
                throw new IllegalArgumentException("test-plan.json entry '" + name + "' has invalid risk partition '" + partition + "'; use stable SPEC IDs such as \"S1.P1\".");
            }
            if (!seam.isBlank() && !partition.startsWith(seam + ".")) {
                throw new IllegalArgumentException("test-plan.json entry '" + name + "' maps seam " + seam + " to risk partition " + partition
                        + ". A test may claim only partitions belonging to its own seam.");
            }
            partitions.add(partition);
        }
        Set<String> distinctPartitions = new HashSet<>(partitions);
        if (distinctPartitions.size() != partitions.size()) {
            throw new IllegalArgumentException("test-plan.json entry '" + name + "' lists a risk partition more than once: " + partitions + ".");
        }
        return List.copyOf(partitions);
    }

    public List<Entry> hiddenEntries() {
        return tests.stream().filter(entry -> "AFTER_DUE_DATE".equals(entry.visibility())).toList();
    }

    /** The entries students can satisfy before the due date and therefore may appear in a statement task binding. */
    public List<Entry> visibleEntries() {
        return tests.stream().filter(entry -> "ALWAYS".equals(entry.visibility())).toList();
    }

    /**
     * Artemis weights individual test cases while the specification assigns importance to a learning seam, so each tier is split evenly across its mapped cases and adding
     * behavioural partitions cannot silently make a seam worth more. Server-seeded structural checks stay zero-weight wherever the seam also has behavioural evidence: Ares
     * reports class, constructor and method structure separately, so otherwise presence checks alone would consume most of the grade. A structural-only seam keeps its tier.
     */
    public Map<String, Double> effectiveWeightsByName() {
        Map<String, Long> behavioralCountBySeam = tests.stream().filter(entry -> !entry.seam().isBlank() && !isStructuralCheck(entry.name()))
                .collect(Collectors.groupingBy(Entry::seam, Collectors.counting()));
        Map<String, Long> totalCountBySeam = tests.stream().filter(entry -> !entry.seam().isBlank()).collect(Collectors.groupingBy(Entry::seam, Collectors.counting()));
        return tests.stream().collect(Collectors.toUnmodifiableMap(Entry::name, entry -> {
            if (entry.seam().isBlank()) {
                return entry.seamWeightTier();
            }
            long behavioralCount = behavioralCountBySeam.getOrDefault(entry.seam(), 0L);
            if (behavioralCount > 0) {
                return isStructuralCheck(entry.name()) ? 0.0 : entry.seamWeightTier() / behavioralCount;
            }
            return entry.seamWeightTier() / totalCountBySeam.get(entry.seam());
        }));
    }

    public static boolean isStructuralCheck(String testName) {
        return testName.startsWith("testClass[") || testName.startsWith("testMethods[") || testName.startsWith("testAttributes[") || testName.startsWith("testConstructors[");
    }
}
