package de.tum.cit.aet.artemis.hyperion.runtime.verification;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.json.JsonMapper;

/**
 * The machine-readable grading plan the TESTS stage writes to {@code /workspace/test-plan.json}, implementing the specification's Testing Strategy with Artemis' native grading
 * features: seam-level importance tiers and {@code AFTER_DUE_DATE} visibility for overfit-resistant hidden variants.
 * <p>
 * A tier belongs to a seam rather than to a single test and is repeated on each of that seam's tests so entries stay self-contained; Artemis stores weights per test case, so the
 * tier is divided evenly across the seam's cases. The seam id itself is transient generation metadata that persistence ignores, and a plan without seams still parses so a
 * candidate verified without them can still be persisted; the TESTS-stage gate is what requires seams wherever the specification declares them. Risk-partition IDs trace the
 * specification's boundary inventory to executable evidence and are likewise generation metadata. Preservation checks protect supplied behavior, carry zero weight, and do not
 * contribute to student-work seams or their risk coverage.
 */
public record GeneratedTestPlan(List<Entry> tests) {

    private static final Pattern SEAM_ID = Pattern.compile("S[1-9][0-9]*");

    private static final Pattern PARTITION_ID = Pattern.compile("S[1-9][0-9]*\\.P[1-9][0-9]*");

    public enum Purpose {
        ASSESSMENT, PRESERVATION
    }

    public record Entry(String name, String seam, double seamWeightTier, String visibility, List<String> riskPartitions, Purpose purpose) {

        public Entry {
            riskPartitions = riskPartitions == null ? List.of() : List.copyOf(riskPartitions);
        }

        public Entry(String name, String seam, double seamWeightTier, String visibility) {
            this(name, seam, seamWeightTier, visibility, List.of());
        }

        public Entry(String name, String seam, double seamWeightTier, String visibility, List<String> riskPartitions) {
            this(name, seam, seamWeightTier, visibility, riskPartitions, Purpose.ASSESSMENT);
        }
    }

    private static final double MIN_SEAM_WEIGHT_TIER = 1.0;

    private static final double MAX_SEAM_WEIGHT_TIER = 3.0;

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /**
     * The file as the agent wrote it, before any of it is checked: the rejection messages name the offending entry, so a value of the wrong shape must not fail while it is
     * read. The values are therefore read as they come, as a string, a number, a boolean, a list or a map, and checked afterwards.
     *
     * @param tests the entries
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RawPlan(@Nullable List<RawEntry> tests) {
    }

    /**
     * One entry of the file as the agent wrote it.
     *
     * @param name           the test name
     * @param seam           the seam ID
     * @param purpose        the purpose of the check
     * @param seamWeightTier the seam importance tier
     * @param weight         the old name of the seam importance tier
     * @param visibility     the visibility of the test
     * @param riskPartitions the risk partition IDs
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RawEntry(@Nullable Object name, @Nullable Object seam, @Nullable Object purpose, @Nullable Object seamWeightTier, @Nullable Object weight,
            @Nullable Object visibility, @Nullable Object riskPartitions) {
    }

    /**
     * The text of a string, a number or a boolean, and the empty text for anything else.
     */
    private static String text(@Nullable Object value) {
        return value instanceof String || value instanceof Number || value instanceof Boolean ? value.toString() : "";
    }

    private static String stripped(@Nullable Object value) {
        return text(value).strip();
    }

    /**
     * Parses and validates the plan.
     *
     * @param json the raw {@code test-plan.json} content
     * @return the validated plan
     * @throws IllegalArgumentException with an actionable, agent-readable message when the JSON is malformed or an entry violates the schema
     */
    public static GeneratedTestPlan parse(String json) {
        RawPlan rawPlan;
        try {
            rawPlan = MAPPER.readValue(json, RawPlan.class);
        }
        catch (MismatchedInputException e) {
            // Well-formed JSON of another shape, or no content at all, has no usable tests and is answered with the expected shape below.
            rawPlan = null;
        }
        catch (JacksonException e) {
            throw new IllegalArgumentException("test-plan.json is not valid JSON: " + e.getOriginalMessage());
        }
        List<RawEntry> rawEntries = rawPlan == null ? null : rawPlan.tests();
        if (rawEntries == null || rawEntries.isEmpty()) {
            throw new IllegalArgumentException("test-plan.json must be {\"tests\":[{\"name\":...,\"seamWeightTier\":1..3,\"visibility\":\"ALWAYS\"|\"AFTER_DUE_DATE\","
                    + "\"riskPartitions\":[\"S1.P1\"]}]} with at least one entry.");
        }
        List<Entry> entries = new ArrayList<>();
        for (RawEntry rawEntry : rawEntries) {
            String name = rawEntry == null ? "" : stripped(rawEntry.name());
            if (name.isBlank()) {
                throw new IllegalArgumentException("Every test-plan.json entry needs a non-empty \"name\" (the exact test name verify reports).");
            }
            Purpose purpose;
            try {
                purpose = Purpose.valueOf(rawEntry.purpose() == null ? "ASSESSMENT" : text(rawEntry.purpose()));
            }
            catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("test-plan.json entry '" + name + "' needs purpose ASSESSMENT or PRESERVATION.");
            }
            String seam = stripped(rawEntry.seam());
            if (!seam.isEmpty() && !SEAM_ID.matcher(seam).matches()) {
                throw new IllegalArgumentException("test-plan.json entry '" + name + "' has seam '" + seam + "'; use a stable SPEC seam ID such as \"S1\".");
            }
            // "weight" is accepted as a read-only alias, but never echoed back: the field carries a seam tier, not the per-case weight persistence derives from it.
            Object tierField = rawEntry.seamWeightTier() != null ? rawEntry.seamWeightTier() : rawEntry.weight();
            if (!(tierField instanceof Number tierNumber)) {
                throw new IllegalArgumentException("test-plan.json entry '" + name + "' needs a numeric \"seamWeightTier\" between " + (int) MIN_SEAM_WEIGHT_TIER + " and "
                        + (int) MAX_SEAM_WEIGHT_TIER + " (core seams weigh more than supporting or edge seams).");
            }
            double tier = tierNumber.doubleValue();
            if (!Double.isFinite(tier) || (purpose == Purpose.ASSESSMENT && (tier < MIN_SEAM_WEIGHT_TIER || tier > MAX_SEAM_WEIGHT_TIER))) {
                throw new IllegalArgumentException("test-plan.json entry '" + name + "' has seamWeightTier " + tier + "; tiers must be between " + (int) MIN_SEAM_WEIGHT_TIER
                        + " and " + (int) MAX_SEAM_WEIGHT_TIER + ".");
            }
            String visibility = stripped(rawEntry.visibility());
            if (!"ALWAYS".equals(visibility) && !"AFTER_DUE_DATE".equals(visibility)) {
                throw new IllegalArgumentException("test-plan.json entry '" + name + "' has visibility '" + visibility + "'; use \"ALWAYS\" or \"AFTER_DUE_DATE\".");
            }
            List<String> riskPartitions = parseRiskPartitions(rawEntry, name, seam);
            if (purpose == Purpose.PRESERVATION && (tier != 0 || !"ALWAYS".equals(visibility) || !seam.isEmpty() || !riskPartitions.isEmpty())) {
                throw new IllegalArgumentException("test-plan.json preservation check '" + name
                        + "' must have seamWeightTier 0, visibility ALWAYS, and no seam or riskPartitions: supplied behavior earns no credit and cannot evidence student work.");
            }
            entries.add(new Entry(name, seam, tier, visibility, riskPartitions, purpose));
        }
        List<String> duplicateNames = entries.stream().map(Entry::name).collect(Collectors.groupingBy(name -> name)).entrySet().stream()
                .filter(group -> group.getValue().size() > 1).map(Map.Entry::getKey).sorted().toList();
        if (!duplicateNames.isEmpty()) {
            throw new IllegalArgumentException("test-plan.json lists these test names more than once: " + duplicateNames + ". Keep exactly one entry per test.");
        }
        return new GeneratedTestPlan(List.copyOf(entries));
    }

    /**
     * Reads and validates the risk partitions of one entry.
     *
     * @param rawEntry the entry as written in the file
     * @param name     the name of the entry, used in the rejection messages
     * @param seam     the validated seam of the entry, possibly empty
     * @return the risk partitions, empty when the entry has none
     * @throws IllegalArgumentException when the partitions are not an array of well-formed IDs of the entry's own seam, or when one is listed twice
     */
    private static List<String> parseRiskPartitions(RawEntry rawEntry, String name, String seam) {
        Object partitionsField = rawEntry.riskPartitions();
        if (partitionsField == null) {
            return List.of();
        }
        if (!(partitionsField instanceof List<?> partitionFields)) {
            throw new IllegalArgumentException("test-plan.json entry '" + name + "' needs \"riskPartitions\" as an array of stable SPEC IDs such as [\"S1.P1\"].");
        }
        List<String> partitions = new ArrayList<>();
        for (Object partitionField : partitionFields) {
            String partition = partitionField instanceof String partitionText ? partitionText.strip() : "";
            if (!PARTITION_ID.matcher(partition).matches()) {
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

    /** Assessed entries visible before the due date; preservation checks remain visible feedback but never become student tasks. */
    public List<Entry> visibleEntries() {
        return assessmentEntries().stream().filter(entry -> "ALWAYS".equals(entry.visibility())).toList();
    }

    public List<Entry> assessmentEntries() {
        return tests.stream().filter(entry -> entry.purpose() == Purpose.ASSESSMENT).toList();
    }

    public List<Entry> preservationEntries() {
        return tests.stream().filter(entry -> entry.purpose() == Purpose.PRESERVATION).toList();
    }

    /**
     * Artemis weights individual test cases while the specification assigns importance to a learning seam, so each tier is split evenly across its mapped cases and adding
     * behavioural partitions cannot silently make a seam worth more. Preservation checks carry zero weight. The plan contains only agent-authored behavioural tests; server-seeded
     * structural tests are identified from
     * their trusted provenance outside this agent-authored file and are therefore never classified by their forgeable names here.
     */
    public Map<String, Double> effectiveWeightsByName() {
        Map<String, Long> totalCountBySeam = assessmentEntries().stream().filter(entry -> !entry.seam().isBlank())
                .collect(Collectors.groupingBy(Entry::seam, Collectors.counting()));
        return tests.stream().collect(Collectors.toUnmodifiableMap(Entry::name, entry -> {
            if (entry.purpose() == Purpose.PRESERVATION) {
                return 0.0;
            }
            if (entry.seam().isBlank()) {
                return entry.seamWeightTier();
            }
            return entry.seamWeightTier() / totalCountBySeam.get(entry.seam());
        }));
    }
}
