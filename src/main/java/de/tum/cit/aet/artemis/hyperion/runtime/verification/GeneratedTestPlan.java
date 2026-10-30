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
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;
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
     * The file as the agent wrote it, before any of it is checked: the rejection messages name the offending entry, so nothing may fail while reading a value of the wrong
     * shape. A {@code null} component means the key was absent, which is not the same as the key being present with a {@code null} or otherwise unusable value.
     *
     * @param tests the entries, absent when {@code tests} is missing or not an array
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RawPlan(@Nullable @JsonDeserialize(using = EntryListDeserializer.class) List<RawEntry> tests) {
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
    private record RawEntry(@Nullable @JsonDeserialize(using = ScalarDeserializer.class) Scalar name, @Nullable @JsonDeserialize(using = ScalarDeserializer.class) Scalar seam,
            @Nullable @JsonDeserialize(using = ScalarDeserializer.class) Scalar purpose, @Nullable @JsonDeserialize(using = ScalarDeserializer.class) Scalar seamWeightTier,
            @Nullable @JsonDeserialize(using = ScalarDeserializer.class) Scalar weight, @Nullable @JsonDeserialize(using = ScalarDeserializer.class) Scalar visibility,
            @Nullable @JsonDeserialize(using = ScalarListDeserializer.class) ScalarList riskPartitions) {

        /** What an entry that is not even an object reads as: every key absent. */
        private static final RawEntry EMPTY = new RawEntry(null, null, null, null, null, null, null);
    }

    /**
     * A JSON value of any shape, reduced to what the validation asks of it.
     *
     * @param text   the text of a string, number or boolean, and the empty text for {@code null}, an object or an array
     * @param string whether the value is a JSON string
     * @param number the value when it is a JSON number, otherwise {@code null}
     */
    private record Scalar(String text, boolean string, @Nullable Double number) {

        /** What an explicit JSON {@code null} and a value of a container shape read as. */
        private static final Scalar NONE = new Scalar("", false, null);
    }

    /**
     * A JSON value that has to be an array, reduced to its entries.
     *
     * @param items the entries, absent when the value is present but is not an array
     */
    private record ScalarList(@Nullable List<Scalar> items) {
    }

    /**
     * Reads a value of any shape as a {@link Scalar}. An explicit {@code null} is a present value and reads as {@link Scalar#NONE}, while an absent key stays {@code null}, which
     * is the distinction the entry validation relies on.
     */
    static final class ScalarDeserializer extends ValueDeserializer<Scalar> {

        @Override
        public Scalar deserialize(JsonParser parser, DeserializationContext context) {
            return readScalar(parser);
        }

        @Override
        public Scalar getNullValue(DeserializationContext context) {
            return Scalar.NONE;
        }

        @Override
        public Scalar getAbsentValue(DeserializationContext context) {
            return null;
        }
    }

    /**
     * Reads a value of any shape as a {@link ScalarList}. An explicit {@code null} is a present value that is not an array, while an absent key stays {@code null}.
     */
    static final class ScalarListDeserializer extends ValueDeserializer<ScalarList> {

        @Override
        public ScalarList deserialize(JsonParser parser, DeserializationContext context) {
            if (parser.currentToken() != JsonToken.START_ARRAY) {
                parser.skipChildren();
                return new ScalarList(null);
            }
            List<Scalar> items = new ArrayList<>();
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                items.add(readScalar(parser));
            }
            return new ScalarList(items);
        }

        @Override
        public ScalarList getNullValue(DeserializationContext context) {
            return new ScalarList(null);
        }

        @Override
        public ScalarList getAbsentValue(DeserializationContext context) {
            return null;
        }
    }

    /**
     * Reads the entries of the file. An element that is not an object still counts as an entry, so that it is rejected for its missing name instead of being skipped; a value
     * that is not an array has no entries.
     */
    static final class EntryListDeserializer extends ValueDeserializer<List<RawEntry>> {

        @Override
        public List<RawEntry> deserialize(JsonParser parser, DeserializationContext context) {
            if (parser.currentToken() != JsonToken.START_ARRAY) {
                parser.skipChildren();
                return null;
            }
            List<RawEntry> entries = new ArrayList<>();
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                if (parser.currentToken() == JsonToken.START_OBJECT) {
                    entries.add(context.readValue(parser, RawEntry.class));
                }
                else {
                    parser.skipChildren();
                    entries.add(RawEntry.EMPTY);
                }
            }
            return entries;
        }
    }

    /**
     * Reads the value the parser is positioned on as a {@link Scalar}, consuming a whole object or array.
     *
     * @param parser the parser positioned on the first token of the value
     * @return the value reduced to its text and, for a number, its numeric value
     */
    private static Scalar readScalar(JsonParser parser) {
        return switch (parser.currentToken()) {
            case VALUE_STRING -> new Scalar(parser.getString(), true, null);
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> new Scalar(parser.getString(), false, parser.getDoubleValue());
            case VALUE_TRUE, VALUE_FALSE -> new Scalar(parser.getString(), false, null);
            default -> {
                parser.skipChildren();
                yield Scalar.NONE;
            }
        };
    }

    private static String stripped(@Nullable Scalar scalar) {
        return scalar == null ? "" : scalar.text().strip();
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
            // Well-formed JSON that is not an object, or no content at all, has no tests and is answered with the expected shape below.
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
            String name = stripped(rawEntry.name());
            if (name.isBlank()) {
                throw new IllegalArgumentException("Every test-plan.json entry needs a non-empty \"name\" (the exact test name verify reports).");
            }
            Purpose purpose;
            try {
                purpose = Purpose.valueOf(rawEntry.purpose() == null ? "ASSESSMENT" : rawEntry.purpose().text());
            }
            catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("test-plan.json entry '" + name + "' needs purpose ASSESSMENT or PRESERVATION.");
            }
            String seam = stripped(rawEntry.seam());
            if (!seam.isEmpty() && !SEAM_ID.matcher(seam).matches()) {
                throw new IllegalArgumentException("test-plan.json entry '" + name + "' has seam '" + seam + "'; use a stable SPEC seam ID such as \"S1\".");
            }
            // "weight" is accepted as a read-only alias, but never echoed back: the field carries a seam tier, not the per-case weight persistence derives from it.
            Scalar tierField = rawEntry.seamWeightTier() != null ? rawEntry.seamWeightTier() : rawEntry.weight();
            if (tierField == null || tierField.number() == null) {
                throw new IllegalArgumentException("test-plan.json entry '" + name + "' needs a numeric \"seamWeightTier\" between " + (int) MIN_SEAM_WEIGHT_TIER + " and "
                        + (int) MAX_SEAM_WEIGHT_TIER + " (core seams weigh more than supporting or edge seams).");
            }
            double tier = tierField.number();
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
        ScalarList partitionsField = rawEntry.riskPartitions();
        if (partitionsField == null) {
            return List.of();
        }
        if (partitionsField.items() == null) {
            throw new IllegalArgumentException("test-plan.json entry '" + name + "' needs \"riskPartitions\" as an array of stable SPEC IDs such as [\"S1.P1\"].");
        }
        List<String> partitions = new ArrayList<>();
        for (Scalar partitionField : partitionsField.items()) {
            String partition = partitionField.string() ? partitionField.text().strip() : "";
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
