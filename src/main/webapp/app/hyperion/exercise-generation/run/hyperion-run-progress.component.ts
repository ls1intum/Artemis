import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TumUiStepComponent, TumUiStepperComponent } from '@tumaet/ui-angular';

import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { HyperionStage } from 'app/hyperion/exercise-generation/model/hyperion-generation-stages';
import { ExerciseGenerationRepairRound } from 'app/openapi/model/exercise-generation-repair-round';

/**
 * The one progress ladder for a Hyperion run. The run page and the code editor's AI activity panel both render this,
 * so a run cannot appear to be at two different points depending on where an instructor is looking.
 */
@Component({
    selector: 'jhi-hyperion-run-progress',
    templateUrl: './hyperion-run-progress.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [ArtemisTranslatePipe, TumUiStepComponent, TumUiStepperComponent],
})
export class HyperionRunProgressComponent {
    readonly stages = input.required<readonly HyperionStage[]>();
    /** The newest message from the server, shown under the stage it belongs to. */
    readonly liveMessage = input<string | undefined>();
    readonly repairRound = input<ExerciseGenerationRepairRound | undefined>();
    /** `compact` drops the detail lines so the ladder fits the code editor's bottom panel. */
    readonly density = input<'full' | 'compact'>('full');

    /** The repair round only makes sense while the review stage is the one running. */
    protected readonly visibleRepairRound = computed(() => {
        const stage = this.stages().find((candidate) => candidate.key === 'review');
        return stage?.state === 'current' ? this.repairRound() : undefined;
    });

    protected readonly detailStageKey = computed(() => this.stages().find((stage) => stage.state === 'current')?.key);
}
