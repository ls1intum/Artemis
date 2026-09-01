import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Subject } from 'rxjs';
import {
    TumUiButtonComponent,
    TumUiButtonDirective,
    TumUiCardComponent,
    TumUiConfirmDialogComponent,
    TumUiConfirmationService,
    TumUiStatusDotComponent,
    TumUiStatusDotState,
} from '@tumaet/ui-angular';
import { TranslateService } from '@ngx-translate/core';

import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { elapsedSecondsSince, serverTimeSignal } from 'app/localci/hyperion-generation-job.utils';
import { formatElapsed } from 'app/hyperion/exercise-generation/model/hyperion-generation-activity';

const CANCEL_CONFIRMATION_KEY = 'hyperionRunCancelConfirmation';

/**
 * What this run is, how it is doing, and what can be done about it.
 *
 * Deliberately reports no percentage and no estimate: the agent's remaining work is not knowable, and a bar that
 * creeps to 90% and stops is a worse answer than a stage name and a clock.
 */
@Component({
    selector: 'jhi-hyperion-run-header',
    templateUrl: './hyperion-run-header.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    providers: [TumUiConfirmationService],
    imports: [
        RouterLink,
        ArtemisTranslatePipe,
        TranslateDirective,
        TumUiButtonComponent,
        TumUiButtonDirective,
        TumUiCardComponent,
        TumUiConfirmDialogComponent,
        TumUiStatusDotComponent,
    ],
})
export class HyperionRunHeaderComponent {
    private readonly confirmationService = inject(TumUiConfirmationService);
    private readonly translateService = inject(TranslateService);

    readonly exerciseTitle = input<string | undefined>();
    /** Translation keys for the meta line, e.g. Java · Maven · Medium. Never raw enum values. */
    readonly metaLabelKeys = input<readonly string[]>([]);
    readonly statusState = input.required<TumUiStatusDotState>();
    readonly statusLabelKey = input.required<string>();
    /** ISO timestamp of the run's STARTED event; without one there is nothing to count from. */
    readonly startedAt = input<string | undefined>();
    /**
     * ISO timestamp of the terminal event.
     *
     * A run opened long after it finished must report how long it took, not how long ago it began, so a finished run
     * is measured against its own end rather than against the clock.
     */
    readonly endedAt = input<string | undefined>();
    /** Whether the run has ended, however it ended. Freezes the clock and swaps Cancel for Run again. */
    readonly terminal = input(false);
    readonly ownedByCaller = input(true);
    readonly cancelAvailable = input(false);
    readonly cancelPending = input(false);
    readonly runAgainAvailable = input(false);
    readonly editorLink = input<readonly (string | number)[] | undefined>();
    readonly exerciseLink = input<readonly (string | number)[] | undefined>();

    readonly cancelRequested = output<void>();
    readonly runAgainRequested = output<void>();

    protected readonly cancelConfirmationKey = CANCEL_CONFIRMATION_KEY;
    /** Read by the code editor to open its AI activity panel instead of the build output it defaults to. */
    protected readonly openGenerationActivityState = { openGenerationActivity: true };

    /** Emits once the run is over, which is what stops the clock from ticking for the rest of the session. */
    private readonly runEnded = new Subject<void>();
    private readonly now = serverTimeSignal(this.runEnded);

    protected readonly elapsed = computed(() => {
        const startedAt = this.startedAt();
        if (!startedAt) {
            return undefined;
        }
        const endedAt = this.endedAt();
        const until = endedAt ? Date.parse(endedAt) : this.now();
        return Number.isFinite(until) ? formatElapsed(elapsedSecondsSince(startedAt, until)) : undefined;
    });

    constructor() {
        effect(() => {
            if (this.terminal()) {
                this.runEnded.next();
            }
        });
    }

    protected confirmCancel(): void {
        this.confirmationService.confirm({
            key: CANCEL_CONFIRMATION_KEY,
            header: this.translateService.instant('artemisApp.hyperion.generation.actions.cancelConfirmHeader'),
            message: this.translateService.instant('artemisApp.hyperion.generation.actions.cancelConfirmMessage'),
            acceptLabel: this.translateService.instant('artemisApp.hyperion.generation.actions.cancelConfirmAccept'),
            rejectLabel: this.translateService.instant('artemisApp.hyperion.generation.actions.cancelConfirmReject'),
            acceptSeverity: 'danger',
            accept: () => this.cancelRequested.emit(),
        });
    }
}
