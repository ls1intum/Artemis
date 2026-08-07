import { TumUiTableDirective, TumUiTagComponent } from '@tumaet/ui-angular';
import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { interval, map } from 'rxjs';
import { GenerationSandboxJob } from 'app/localci/shared/entities/generation-sandbox-job.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisDurationFromSecondsPipe } from 'app/foundation/pipes/artemis-duration-from-seconds.pipe';
import { ArtemisTimeAgoPipe } from 'app/foundation/pipes/artemis-time-ago.pipe';
import { ArtemisServerDateService } from 'app/foundation/service/server-date.service';

@Component({
    selector: 'jhi-hyperion-generation-jobs-table',
    templateUrl: './hyperion-generation-jobs-table.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [RouterLink, TumUiTableDirective, TumUiTagComponent, TranslateDirective, ArtemisTranslatePipe, ArtemisDatePipe, ArtemisDurationFromSecondsPipe, ArtemisTimeAgoPipe],
})
export class HyperionGenerationJobsTableComponent {
    private static readonly CLOCK_INTERVAL_MS = 1000;

    readonly jobs = input.required<GenerationSandboxJob[]>();
    readonly showAgent = input(false);
    private readonly serverDateService = inject(ArtemisServerDateService);

    private readonly now = toSignal(interval(HyperionGenerationJobsTableComponent.CLOCK_INTERVAL_MS).pipe(map(() => this.serverDateService.now().valueOf())), {
        initialValue: this.serverDateService.now().valueOf(),
    });

    elapsedSeconds(timestamp: string): number {
        return Math.max(0, Math.floor((this.now() - Date.parse(timestamp)) / 1000));
    }

    modeKey(mode: GenerationSandboxJob['mode']): string {
        return `artemisApp.buildAgents.generationSandboxes.${mode === 'ADAPT' ? 'adapt' : 'generate'}`;
    }
}
