import { describe, expect, it } from 'vitest';
import { HyperionJobEntry } from 'app/hyperion/exercise-generation/state/hyperion-job-registry.service';
import { authoringActivity, variantActivity } from './hyperion-activity.model';

const entry: HyperionJobEntry = {
    jobId: 'same-id',
    exerciseId: 2,
    courseId: 1,
    exerciseTitle: 'Stacks',
    mode: 'ADAPT',
    startedAt: '2026-09-21T12:00:00Z',
    status: 'running',
    cancellable: true,
    seen: false,
};

describe('Unified AI activity presentation', () => {
    it('namespaces job identities and keeps the canonical programming route', () => {
        const authoring = authoringActivity(entry);
        const variant = variantActivity({ jobId: entry.jobId, phase: 'ANALYZING' });
        expect(authoring.key).not.toBe(variant.key);
        expect(authoring.dismissalKey).not.toBe(variant.dismissalKey);
        expect(authoring.routerLink).toEqual(['/course-management', 1, 'programming-exercises', 2, 'generation', 'runs', 'same-id']);
    });

    it('distinguishes a programming variant by source and destination without inventing another pipeline', () => {
        const variant = authoringActivity({ ...entry, sourceExerciseId: 1, phase: 'VERIFYING' });
        expect(variant.kindKey).toBe('artemisApp.hyperion.activity.kind.variant');
        expect(variant.steps).toEqual(authoringActivity({ ...entry, phase: 'VERIFYING' }).steps);
        expect(authoringActivity({ ...entry, sourceExerciseId: entry.exerciseId }).kindKey).toBe('artemisApp.hyperion.activity.kind.adapt');
    });

    it('uses the same stage ordering as the run page and moves back for a repair', () => {
        const reviewing = authoringActivity({ ...entry, phase: 'REVIEWING' });
        const repairing = authoringActivity({ ...entry, phase: 'REPAIRING' });
        expect(reviewing.steps.findIndex((step) => step.current)).toBe(3);
        expect(repairing.steps.findIndex((step) => step.current)).toBe(1);
        expect(repairing.steps[1].labelKey).toBe('artemisApp.hyperion.activity.stage.revise');
        expect(reviewing.dismissalKey).toBe(repairing.dismissalKey);
    });

    it('does not invent a stage before server progress arrives', () => {
        expect(authoringActivity(entry).steps.some((step) => step.current)).toBe(false);
    });

    it.each(['saved', 'needsReview', 'partial', 'failed', 'cancelled', 'unknown'] as const)('terminal %s wins over a stale phase', (status) => {
        const row = authoringActivity({ ...entry, phase: 'SAVING', status });
        expect(row.active).toBe(false);
        expect(row.canCancel).toBe(false);
        expect(row.steps).toEqual([]);
        expect(row.dismissalKey).not.toBe(authoringActivity(entry).dismissalKey);
    });

    it('does not offer cancellation beyond either workflow persistence boundary', () => {
        expect(authoringActivity({ ...entry, phase: 'SAVING' }).canCancel).toBe(false);
        expect(variantActivity({ jobId: 'v', phase: 'FINALIZING' }).canCancel).toBe(false);
        expect(authoringActivity({ ...entry, status: 'cancelling' }).canCancel).toBe(false);
    });

    it('keeps partial writes and surviving cancelled clones as recovery obligations', () => {
        expect(authoringActivity({ ...entry, status: 'partial' }).recoveryRequired).toBe(true);
        expect(variantActivity({ jobId: 'v', phase: 'CANCELLED', variantExerciseId: 5 }).recoveryRequired).toBe(true);
        expect(variantActivity({ jobId: 'v', phase: 'CANCELLED' }).recoveryRequired).toBe(false);
        expect(variantActivity({ jobId: 'v', phase: 'FAILED', variantExerciseId: 5 }).recoveryRequired).toBe(true);
    });

    it('preserves quiz labels, phase progression and warning outcomes without changing its protocol', () => {
        const job = { jobId: 'q', exerciseType: 'quiz' as const, sourceExerciseTitle: 'Quiz', variantExerciseTitle: 'Quiz II', phase: 'REPAIRING' as const };
        const row = variantActivity(job);
        expect(row.source).toEqual({ kind: 'variant', job });
        expect(row.kindKey).toBe('artemisApp.hyperion.activity.kind.quizVariant');
        expect(row.targetTitle).toBe('Quiz II');
        expect(row.steps.findIndex((step) => step.current)).toBe(5);
        expect(variantActivity({ ...job, phase: 'DRAFT_WITH_WARNINGS' }).attention).toBe(true);
    });
});
