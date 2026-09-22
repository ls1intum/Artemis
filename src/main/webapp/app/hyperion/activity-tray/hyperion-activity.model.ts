import { HyperionJobEntry, isTerminalHyperionJobStatus } from 'app/hyperion/exercise-generation/state/hyperion-job-registry.service';
import { HYPERION_STAGES, stageIndexOfPhase } from 'app/hyperion/exercise-generation/model/hyperion-generation-stages';
import { VariantJob } from 'app/openapi/model/variant-job';
import { isTerminalVariantPhase } from 'app/hyperion/services/exercise-variant-websocket.service';

const VARIANT_STAGES = ['analyze', 'plan', 'write', 'verify', 'save'] as const;
const VARIANT_STAGE_INDEX: Record<string, number> = { ANALYZING: 0, PLANNING: 1, PROVISIONING: 2, TRANSFORMING: 2, VERIFYING: 3, REPAIRING: 3, FINALIZING: 4 };

export type ActivitySource = { kind: 'authoring'; entry: HyperionJobEntry } | { kind: 'variant'; job: VariantJob };

/** Presentation only: transport, cancellation and persistence remain owned by the respective workflow. */
export interface HyperionActivityRow {
    key: string;
    source: ActivitySource;
    title: string;
    targetTitle?: string;
    kindKey: string;
    statusKey: string;
    active: boolean;
    attention: boolean;
    seen: boolean;
    recoveryRequired: boolean;
    canCancel: boolean;
    message?: string;
    startedAt: number;
    /** A hidden running job reappears when its outcome changes, not on every progress event. */
    dismissalKey: string;
    steps: { labelKey: string; current: boolean; complete: boolean }[];
}

export function authoringActivity(entry: HyperionJobEntry): HyperionActivityRow {
    const active = !isTerminalHyperionJobStatus(entry.status);
    const current = stageIndexOfPhase(entry.phase);
    return {
        key: `authoring:${entry.jobId}`,
        source: { kind: 'authoring', entry },
        title: entry.exerciseTitle,
        kindKey: `artemisApp.hyperion.activity.kind.${entry.kind === 'VARIANT' || (entry.sourceExerciseId && entry.sourceExerciseId !== entry.exerciseId) ? 'variant' : entry.mode === 'ADAPT' ? 'adapt' : 'create'}`,
        statusKey: `artemisApp.hyperion.generation.status.${entry.status}`,
        active,
        seen: entry.seen,
        attention: ['needsReview', 'partial', 'failed', 'unknown'].includes(entry.status),
        recoveryRequired: entry.status === 'partial',
        canCancel: active && entry.cancellable === true && entry.status !== 'cancelling' && entry.phase !== 'SAVING',
        message: entry.message,
        startedAt: Date.parse(entry.startedAt) || 0,
        dismissalKey: `authoring:${entry.jobId}:${active ? 'active' : entry.status}`,
        steps: active
            ? HYPERION_STAGES.map((stage, index) => ({
                  labelKey: `artemisApp.hyperion.activity.stage.${stage.key === 'design' && entry.mode === 'ADAPT' ? 'revise' : stage.key}`,
                  current: index === current,
                  complete: current !== undefined && index < current,
              }))
            : [],
    };
}

export function variantActivity(job: VariantJob): HyperionActivityRow {
    const active = !isTerminalVariantPhase(job.phase);
    const recoveryRequired = (job.phase === 'CANCELLED' || job.phase === 'FAILED') && job.variantExerciseId !== undefined;
    return {
        key: `variant:${job.jobId}`,
        source: { kind: 'variant', job },
        title: job.sourceExerciseTitle ?? '',
        targetTitle: job.variantExerciseTitle,
        kindKey: `artemisApp.hyperion.activity.kind.${job.exerciseType === 'quiz' ? 'quizVariant' : 'variant'}`,
        statusKey: job.phase ? `artemisApp.exerciseVariantGeneration.phase.${job.phase}` : 'artemisApp.hyperion.generation.status.unknown',
        active,
        seen: false,
        attention: job.phase === 'FAILED' || job.phase === 'DRAFT_WITH_WARNINGS' || recoveryRequired,
        recoveryRequired,
        canCancel: active && job.phase !== undefined && job.phase !== 'FINALIZING',
        message: job.failureDetail,
        startedAt: Date.parse(job.startedAt ?? '') || 0,
        dismissalKey: `variant:${job.jobId}:${active ? 'active' : job.phase}`,
        steps: active
            ? VARIANT_STAGES.map((stage, index) => ({
                  labelKey: `artemisApp.hyperion.activity.variantStage.${stage}`,
                  current: index === (VARIANT_STAGE_INDEX[job.phase ?? ''] ?? 0),
                  complete: index < (VARIANT_STAGE_INDEX[job.phase ?? ''] ?? 0),
              }))
            : [],
    };
}
