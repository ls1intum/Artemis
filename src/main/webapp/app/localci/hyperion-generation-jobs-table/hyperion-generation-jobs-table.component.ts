import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { interval, map } from 'rxjs';
import { TumUiTableDirective } from 'app/shared-ui/tum-ui/table-directive/tum-ui-table.directive';
import { TumUiTagComponent } from 'app/shared-ui/tum-ui/tag/tum-ui-tag.component';
import { GenerationSandboxJob } from 'app/localci/shared/entities/generation-sandbox-job.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisDurationFromSecondsPipe } from 'app/foundation/pipes/artemis-duration-from-seconds.pipe';
import { ArtemisTimeAgoPipe } from 'app/foundation/pipes/artemis-time-ago.pipe';

@Component({
    selector: 'jhi-hyperion-generation-jobs-table',
    templateUrl: './hyperion-generation-jobs-table.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [RouterLink, TumUiTableDirective, TumUiTagComponent, TranslateDirective, ArtemisTranslatePipe, ArtemisDatePipe, ArtemisDurationFromSecondsPipe, ArtemisTimeAgoPipe],
})
export class HyperionGenerationJobsTableComponent {
    /** How often the elapsed-duration column re-renders. */
    private static readonly CLOCK_INTERVAL_MS = 1000;

    readonly jobs = input.required<GenerationSandboxJob[]>();
    readonly showAgent = input(false);

    /**
     * Ticks once per second so the elapsed duration column re-renders; the subscription is torn down with the component.
     * NOTE: for clock-skew correctness this should read `ArtemisServerDateService.now()` instead of the raw client clock;
     * that behaviour is deliberately left unchanged here.
     */
    private readonly now = toSignal(interval(HyperionGenerationJobsTableComponent.CLOCK_INTERVAL_MS).pipe(map(() => Date.now())), { initialValue: Date.now() });

    elapsedSeconds(timestamp: string): number {
        return Math.max(0, Math.floor((this.now() - Date.parse(timestamp)) / 1000));
    }

    modeKey(mode: GenerationSandboxJob['mode']): string {
        return `artemisApp.buildAgents.generationSandboxes.${mode === 'ADAPT' ? 'adapt' : 'generate'}`;
    }
}
