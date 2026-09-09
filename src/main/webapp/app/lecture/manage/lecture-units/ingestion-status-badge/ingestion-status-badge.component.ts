import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCircleMinus, faClock, faExclamationTriangle, faFileLines, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { LectureUnitProcessingStatus, ProcessingPhase } from 'app/lecture/manage/lecture-units/services/lecture-unit.service';

/**
 * The visual states the badge can render. Derived from the processing phase plus the live stage
 * ledger, so an INGESTING unit in its audit stage reads as "Verifying" rather than "Indexing".
 */
export type IngestionBadgeState = 'queued' | 'transcribing' | 'indexing' | 'verifying' | 'done' | 'failed' | 'skipped';

/**
 * Single status badge for the ingestion pipeline of a lecture unit.
 * <p>
 * Renders the current phase with the live stage counter Iris reports through its heartbeats
 * ("Indexing · 41/142") and a hairline progress bar. This component owns the whole mapping from
 * status to visuals, so richer observability (a details card with stage timings, quality, and
 * verification) can later attach here without touching the management component.
 */
@Component({
    selector: 'jhi-ingestion-status-badge',
    templateUrl: './ingestion-status-badge.component.html',
    styleUrls: ['./ingestion-status-badge.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FaIconComponent, NgbTooltip, TranslateDirective, ArtemisTranslatePipe],
})
export class IngestionStatusBadgeComponent {
    protected readonly faClock = faClock;
    protected readonly faSpinner = faSpinner;
    protected readonly faFileLines = faFileLines;
    protected readonly faExclamationTriangle = faExclamationTriangle;
    protected readonly faCircleMinus = faCircleMinus;

    /** The unit's processing status; undefined while nothing is known yet. */
    status = input<LectureUnitProcessingStatus | undefined>(undefined);

    /** True when an IDLE (or unknown) unit will actually be picked up, so "Queued" is honest. */
    awaiting = input(false);

    /** Stage names Iris reports for the read-back audit at the end of a run. */
    private static readonly VERIFYING_STAGES = new Set(['audit']);

    state = computed<IngestionBadgeState | undefined>(() => {
        const status = this.status();
        const phase = status?.phase;
        if (phase === undefined || phase === ProcessingPhase.IDLE) {
            return this.awaiting() ? 'queued' : undefined;
        }
        switch (phase) {
            case ProcessingPhase.TRANSCRIBING:
                return 'transcribing';
            case ProcessingPhase.INGESTING:
                return status?.stageName && IngestionStatusBadgeComponent.VERIFYING_STAGES.has(status.stageName) ? 'verifying' : 'indexing';
            case ProcessingPhase.DONE:
                return 'done';
            case ProcessingPhase.FAILED:
                return 'failed';
            case ProcessingPhase.SKIPPED:
                return 'skipped';
            default: {
                // Compile-time guard: a new ProcessingPhase must be mapped here explicitly rather
                // than silently falling through to an unrendered badge.
                const exhaustiveCheck: never = phase;
                return exhaustiveCheck;
            }
        }
    });

    /** Live counter within the current stage, e.g. "41/142"; undefined when the stage reports none. */
    progressText = computed<string | undefined>(() => {
        const status = this.status();
        if (!this.isRunning() || status?.stageProgress == null || !status.stageTotal) {
            return undefined;
        }
        return `${status.stageProgress}/${status.stageTotal}`;
    });

    /** Width of the hairline progress bar in percent; undefined without a counter. */
    progressPercent = computed<number | undefined>(() => {
        const status = this.status();
        if (!this.isRunning() || status?.stageProgress == null || !status.stageTotal) {
            return undefined;
        }
        return Math.min(100, Math.round((status.stageProgress / status.stageTotal) * 100));
    });

    isRunning = computed(() => {
        const state = this.state();
        return state === 'transcribing' || state === 'indexing' || state === 'verifying';
    });

    tooltipKey = computed<string | undefined>(() => {
        const state = this.state();
        if (state === 'failed') {
            return this.status()?.errorKey ?? 'artemisApp.attachmentVideoUnit.processing.error.processingFailed';
        }
        if (state === 'skipped') {
            return 'artemisApp.attachmentVideoUnit.processingSkippedTooltip';
        }
        return undefined;
    });

    labelKey = computed<string>(() => {
        switch (this.state()) {
            case 'queued':
                return 'artemisApp.attachmentVideoUnit.awaitingProcessing';
            case 'transcribing':
                return 'artemisApp.attachmentVideoUnit.processingTranscribing';
            case 'indexing':
                return 'artemisApp.attachmentVideoUnit.processingIngesting';
            case 'verifying':
                return 'artemisApp.attachmentVideoUnit.processingVerifying';
            case 'done':
                return 'artemisApp.attachmentVideoUnit.processingComplete';
            case 'failed':
                return 'artemisApp.attachmentVideoUnit.processingFailed';
            case 'skipped':
                return 'artemisApp.attachmentVideoUnit.processingSkipped';
            default:
                return '';
        }
    });

    badgeClass = computed<string>(() => {
        switch (this.state()) {
            case 'queued':
            case 'skipped':
                return 'bg-secondary';
            case 'transcribing':
            case 'indexing':
            case 'verifying':
                return 'bg-info';
            case 'done':
                return 'bg-success';
            case 'failed':
                return 'bg-danger';
            default:
                return '';
        }
    });
}
