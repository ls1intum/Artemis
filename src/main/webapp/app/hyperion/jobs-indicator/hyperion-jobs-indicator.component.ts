import { ChangeDetectionStrategy, Component, computed, inject, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faXmark } from '@fortawesome/free-solid-svg-icons';
import { TumUiButtonDirective, TumUiPopoverComponent, TumUiPopoverTriggerDirective, TumUiStatusDotComponent, TumUiStatusDotState, TumUiTagComponent } from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { HyperionJobEntry, HyperionJobIndicatorState, HyperionJobRegistryService, HyperionJobStatus } from 'app/hyperion/exercise-generation/state/hyperion-job-registry.service';

/** How the aggregate indicator state is shown in the navbar dot. */
const INDICATOR_DOT_STATE: Record<HyperionJobIndicatorState, TumUiStatusDotState> = {
    idle: 'neutral',
    running: 'running',
    attention: 'warning',
    success: 'success',
};

/** How a single run's status is shown in the tray. */
const ENTRY_DOT_STATE: Record<HyperionJobStatus, TumUiStatusDotState> = {
    queued: 'queued',
    running: 'running',
    cancelling: 'running',
    saved: 'success',
    needsReview: 'warning',
    partial: 'warning',
    failed: 'error',
    cancelled: 'neutral',
    unknown: 'neutral',
};

/**
 * Navbar tray listing the AI exercise-generation runs this browser started or observed.
 *
 * Renders nothing at all while there is nothing to report, so the navbar stays quiet for everyone who is not
 * currently generating an exercise.
 */
@Component({
    selector: 'jhi-hyperion-jobs-indicator',
    templateUrl: './hyperion-jobs-indicator.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        RouterLink,
        FaIconComponent,
        ArtemisTranslatePipe,
        TranslateDirective,
        TumUiButtonDirective,
        TumUiPopoverComponent,
        TumUiPopoverTriggerDirective,
        TumUiStatusDotComponent,
        TumUiTagComponent,
    ],
})
export class HyperionJobsIndicatorComponent {
    private readonly registry = inject(HyperionJobRegistryService);

    protected readonly faXmark = faXmark;

    protected readonly entries = this.registry.entries;
    protected readonly unseenCount = this.registry.unseenCount;
    protected readonly indicatorState = this.registry.indicatorState;
    protected readonly loadFailed = this.registry.loadFailed;

    protected readonly indicatorDotState = computed(() => INDICATOR_DOT_STATE[this.indicatorState()]);

    private readonly popover = viewChild(TumUiPopoverComponent);

    /** The dot state for a single run. */
    protected entryDotState(entry: HyperionJobEntry): TumUiStatusDotState {
        return ENTRY_DOT_STATE[entry.status] ?? 'neutral';
    }

    /** The translation key describing a single run's status. */
    protected entryStatusKey(entry: HyperionJobEntry): string {
        return `artemisApp.hyperion.generation.status.${entry.status}`;
    }

    /** Opening a run counts as having seen it, so the badge clears. */
    protected openEntry(entry: HyperionJobEntry): void {
        this.registry.markSeen(entry.jobId);
        this.popover()?.close();
    }

    protected dismiss(jobId: string): void {
        this.registry.dismiss(jobId);
    }

    protected retry(): void {
        this.registry.refresh();
    }
}
