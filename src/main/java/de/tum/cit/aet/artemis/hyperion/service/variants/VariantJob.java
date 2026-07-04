package de.tum.cit.aet.artemis.hyperion.service.variants;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.hyperion.dto.VariantGenerationRequestDTO;

/**
 * The distributed job record for one variant-generation run (plan Sections 2.2 and 2.7.1).
 * Lives in a Hazelcast map owned by {@link ExerciseVariantJobService} — exactly like
 * {@code HyperionCodeGenerationJobService.JobInfo}, but mutable-by-replacement: the pipeline reads the
 * entry, mutates a copy, and puts it back so updates are visible cluster-wide (this is what makes
 * background generation, reconnect, and the navbar job tray possible, Section 5.4).
 *
 * NOTE: mutation MUST always go through {@link ExerciseVariantJobService} (single writer per field group)
 * so websocket events and map state stay consistent.
 */
public class VariantJob implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // TODO (Opus): Decide final shape — either keep this mutable class (fields below) or convert to an immutable
    // record with wither-style copies; either way every field listed in plan Section 2.2 must be present:
    // jobId, sourceExerciseId, initiating user, phase, attempt counter, ChangePlan, per-phase step outputs,
    // accumulated verifier findings/warnings, token usage. Add getters/setters accordingly.

    private String jobId;

    private Long sourceExerciseId;

    private String sourceExerciseTitle;   // shown in the tray list (plan Section 5.4)

    private ExerciseType exerciseType;    // resolved server-side from the source exercise (plan Section 5.1)

    private String initiatorLogin;        // per-user scoping of the variant-jobs endpoints (plan Section 5.1)

    private VariantJobPhase phase;

    private int attempt;                  // repair attempt counter, shown as "attempt 2/3" (plan Section 5.2)

    private boolean cancelRequested;      // cooperative-cancel flag, set via DELETE /variant-jobs/{jobId} (plan Section 5.2)

    private ChangePlan changePlan;

    private Map<VariantJobPhase, StepOutput> stepOutputs = new EnumMap<>(VariantJobPhase.class);

    private List<String> warnings = new ArrayList<>();

    private Long variantExerciseId;       // set in PROVISIONING; the tray deep-links to it on COMPLETED/DRAFT_WITH_WARNINGS (plan Section 5.4)

    private VariantGenerationRequestDTO request;

    private Instant startedAt;

    private Instant finishedAt;

    // TODO (Sonnet): Add token/latency telemetry fields (tokens per phase via LLMTokenUsageService, wall time)
    // needed for the thesis evaluation metrics in plan Section 7 ("Per-job telemetry: tokens, attempts per phase,
    // wall time").

    // TODO (Sonnet): Add a `nodeStartupMarker`/`staleness` mechanism so that after a server restart mid-job the
    // `active` endpoint can report FAILED-stale instead of a forever-running job (plan Section 6, "Server restart
    // mid-job" row; full resume is explicitly future work, Section 11).

    // TODO (Sonnet): Generate getters and setters for all fields (no Lombok in this codebase).
}
