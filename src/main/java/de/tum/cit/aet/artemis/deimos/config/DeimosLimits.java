package de.tum.cit.aet.artemis.deimos.config;

/**
 * Defaults and bounds of the configurable Deimos limits.
 * <p>
 * Every {@code @Value} fallback for these properties refers to the constants here, so the services, the startup
 * validation and the value published to the client cannot drift apart. {@code application.yml} repeats the defaults
 * for administrators; a test keeps it in line with this class.
 */
public final class DeimosLimits {

    public static final String MAX_FILE_INPUT_BYTES_PROPERTY = "artemis.deimos.payload.max-file-input-bytes";

    public static final String MAX_FILE_DIFF_BYTES_PROPERTY = "artemis.deimos.payload.max-file-diff-bytes";

    public static final String MAX_PAYLOAD_BYTES_PROPERTY = "artemis.deimos.payload.max-payload-bytes";

    public static final String FINAL_STATE_RESERVED_BYTES_PROPERTY = "artemis.deimos.payload.final-state-reserved-bytes";

    public static final String MAX_PARTICIPATIONS_PER_RUN_PROPERTY = "artemis.deimos.batch.max-participations-per-run";

    public static final String MAX_WINDOW_DAYS_PROPERTY = "artemis.deimos.batch.max-window-days";

    /**
     * Files larger than this are not diffed at all.
     */
    public static final int DEFAULT_MAX_FILE_INPUT_BYTES = 256 * 1024;

    /**
     * Maximum size of the unified diff emitted for a single file.
     */
    public static final int DEFAULT_MAX_FILE_DIFF_BYTES = 32 * 1024;

    /**
     * Maximum size of the whole untrusted payload handed to the model.
     */
    public static final int DEFAULT_MAX_PAYLOAD_BYTES = 128 * 1024;

    /**
     * Share of the payload held back for the final cumulative diff.
     */
    public static final int DEFAULT_FINAL_STATE_RESERVED_BYTES = 32 * 1024;

    public static final long DEFAULT_MAX_PARTICIPATIONS_PER_RUN = 5000;

    public static final long DEFAULT_MAX_WINDOW_DAYS = 31;

    /**
     * Lower bound for every byte limit. The notices appended after the incremental snapshots, the final-state header
     * and the space each diff keeps for its own omission notice all have to fit inside the final-state reservation;
     * below this size the payload would no longer be bounded by the configured total.
     */
    public static final int MIN_BYTE_LIMIT = 1024;

    private DeimosLimits() {
    }
}
