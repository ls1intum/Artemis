import { ChangeDetectionStrategy, Component, OnDestroy, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { GenerationSandboxJob } from 'app/localci/shared/entities/generation-sandbox-job.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisDurationFromSecondsPipe } from 'app/foundation/pipes/artemis-duration-from-seconds.pipe';

@Component({
    selector: 'jhi-hyperion-generation-jobs-table',
    templateUrl: './hyperion-generation-jobs-table.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [RouterLink, TableModule, TagModule, TranslateDirective, ArtemisTranslatePipe, ArtemisDatePipe, ArtemisDurationFromSecondsPipe],
})
export class HyperionGenerationJobsTableComponent implements OnDestroy {
    readonly jobs = input.required<GenerationSandboxJob[]>();
    readonly showAgent = input(false);
    readonly now = signal(Date.now());

    private readonly clock = setInterval(() => this.now.set(Date.now()), 1000);

    ngOnDestroy(): void {
        clearInterval(this.clock);
    }

    elapsedSeconds(timestamp: string): number {
        return Math.max(0, Math.floor((this.now() - Date.parse(timestamp)) / 1000));
    }

    modeKey(mode: GenerationSandboxJob['mode']): string {
        return `artemisApp.buildAgents.generationSandboxes.${mode === 'ADAPT' ? 'adapt' : 'generate'}`;
    }
}
