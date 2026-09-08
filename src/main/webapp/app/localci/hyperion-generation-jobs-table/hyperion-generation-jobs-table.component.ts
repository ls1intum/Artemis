import { TumUiStatusDotComponent, TumUiTableDirective } from '@tumaet/ui-angular';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { GenerationSandboxJob } from 'app/localci/shared/entities/generation-sandbox-job.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisDurationFromSecondsPipe } from 'app/foundation/pipes/artemis-duration-from-seconds.pipe';
import { ArtemisTimeAgoPipe } from 'app/foundation/pipes/artemis-time-ago.pipe';
import { elapsedSecondsSince, generationModeLabelKey, serverTimeSignal } from 'app/localci/hyperion-generation-job.utils';

/** A table row: the job plus the values the template would otherwise have to derive per change detection. */
interface GenerationJobRow {
    job: GenerationSandboxJob;
    modeLabelKey: string;
    elapsedSeconds: number;
}

@Component({
    selector: 'jhi-hyperion-generation-jobs-table',
    templateUrl: './hyperion-generation-jobs-table.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        RouterLink,
        TumUiStatusDotComponent,
        TumUiTableDirective,
        TranslateDirective,
        ArtemisTranslatePipe,
        ArtemisDatePipe,
        ArtemisDurationFromSecondsPipe,
        ArtemisTimeAgoPipe,
    ],
})
export class HyperionGenerationJobsTableComponent {
    readonly jobs = input.required<GenerationSandboxJob[]>();
    readonly showAgent = input(false);

    private readonly now = serverTimeSignal();

    readonly rows = computed<GenerationJobRow[]>(() =>
        this.jobs().map((job) => ({
            job,
            modeLabelKey: generationModeLabelKey(job.mode),
            elapsedSeconds: elapsedSecondsSince(job.startedAt, this.now()),
        })),
    );
}
