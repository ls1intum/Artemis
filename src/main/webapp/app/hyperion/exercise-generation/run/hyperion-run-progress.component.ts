import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TumUiStepComponent, TumUiStepState, TumUiStepperComponent } from '@tumaet/ui-angular';

import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { HyperionActivityView } from 'app/hyperion/exercise-generation/model/hyperion-generation-activity';
import { HyperionStage, HyperionStageKey, HyperionSubstepKey } from 'app/hyperion/exercise-generation/model/hyperion-generation-stages';
import { HyperionRunActivityComponent } from 'app/hyperion/exercise-generation/run/hyperion-run-activity.component';
import { ExerciseGenerationRepairRound } from 'app/openapi/model/exercise-generation-repair-round';

interface ProgressSubstep {
    key: HyperionSubstepKey;
    state: TumUiStepState;
    labelKey: string;
}

/** One rung of the ladder with every binding resolved, so no template binding calls a method. */
interface ProgressStep {
    key: HyperionStageKey;
    state: TumUiStepState;
    labelKey: string;
    substeps?: ProgressSubstep[];
    summaryKey?: string;
    summaryParams?: { turns: number; files: number };
    /** The one step that carries the live region and the activity panel. */
    detail: boolean;
}

/**
 * The one progress ladder for a Hyperion run. The run page and the code editor's AI activity panel both render this,
 * so a run cannot appear to be at two different points depending on where an instructor is looking.
 *
 * Everything the agent is doing is reported inside the ladder: the design stage's substeps as a nested ladder, and
 * the live line, the clock, the counters and the recent messages under the stage that is running. There is no second
 * place to look, and no disclosure to open first.
 */
@Component({
    selector: 'jhi-hyperion-run-progress',
    templateUrl: './hyperion-run-progress.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [ArtemisTranslatePipe, HyperionRunActivityComponent, TumUiStepComponent, TumUiStepperComponent],
})
export class HyperionRunProgressComponent {
    readonly stages = input.required<readonly HyperionStage[]>();
    /** The newest message from the server, shown under the stage it belongs to. */
    readonly liveMessage = input<string | undefined>();
    readonly repairRound = input<ExerciseGenerationRepairRound | undefined>();
    /** What the agent is doing, shown under the same stage as the live message. */
    readonly activity = input<HyperionActivityView | undefined>();
    /** `compact` drops the recent-activity list so the ladder fits the code editor's bottom panel. */
    readonly density = input<'full' | 'compact'>('full');

    /** The repair round only makes sense while the review stage is the one running. */
    protected readonly visibleRepairRound = computed(() => {
        const stage = this.stages().find((candidate) => candidate.key === 'review');
        return stage?.state === 'current' ? this.repairRound() : undefined;
    });

    /**
     * The stage the detail belongs under: the one that is running, or - once the run is over - the one it stopped in,
     * so the last thing the agent said does not vanish the moment the run ends.
     */
    protected readonly detailStageKey = computed<HyperionStageKey | undefined>(() => {
        const stages = this.stages();
        return (stages.find((stage) => stage.state === 'current') ?? stages.findLast((stage) => stage.state === 'failed') ?? stages.findLast((stage) => stage.state === 'complete'))
            ?.key;
    });

    protected readonly steps = computed<ProgressStep[]>(() => {
        const detailKey = this.detailStageKey();
        return this.stages().map((stage) => ({
            key: stage.key,
            state: stage.state,
            labelKey: `artemisApp.hyperion.generation.stage.${stage.key}`,
            // A stage nobody has reached yet must not preview the work it might do.
            substeps:
                stage.substeps && stage.state !== 'pending'
                    ? stage.substeps.map((substep) => ({ key: substep.key, state: substep.state, labelKey: `artemisApp.hyperion.generation.substep.${substep.key}` }))
                    : undefined,
            // Files are omitted when the stage wrote none, for the same reason the meter omits a counter at zero.
            summaryKey: stage.summary ? (stage.summary.files > 0 ? 'artemisApp.hyperion.generation.stageSummary' : 'artemisApp.hyperion.generation.stageSummaryTurns') : undefined,
            summaryParams: stage.summary,
            detail: stage.key === detailKey,
        }));
    });
}
