import { ChangeDetectionStrategy, Component, computed, effect, input, signal } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faSpinner } from '@fortawesome/free-solid-svg-icons';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { TumUiTagComponent, TumUiTagSeverity } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { LectureUnitProcessingStatus, ProcessingPhase } from 'app/lecture/manage/lecture-units/services/lecture-unit.service';

/**
 * The visual states the badge can render. Derived from the processing phase plus the live stage
 * ledger, so an INGESTING unit in its audit stage reads as "Verifying" rather than "Indexing".
 */
export type IngestionBadgeState = 'queued' | 'transcribing' | 'indexing' | 'verifying' | 'lost' | 'done' | 'failed' | 'skipped';

/** How often the live elapsed-time readout re-computes while a unit is running. */
const ELAPSED_TICK_MS = 1000;

/**
 * How long after the last received lease renewal a running unit counts as having lost contact. The
 * worker renews every 5 seconds, so this is four missed renewals — the Kubernetes node-lease ratio.
 * Measured against the renewal's local receipt time on the same clock as the badge tick, so server
 * clock skew cannot fake or hide a lost run.
 */
const LOST_CONTACT_AFTER_MS = 20_000;

/**
 * A run whose server-stamped heartbeat is older than this on arrival is lost regardless of how
 * fresh its local receipt is: it covers a dead run rendered from the initial page-load snapshot,
 * where the receipt time is always fresh. Cross-clock comparison is safe at minutes granularity.
 */
const LOST_CONTACT_SERVER_AGE_MS = 120_000;

/**
 * Single status badge for the ingestion pipeline of a lecture unit.
 * <p>
 * Renders the current phase as a TUM UI tag whose label follows the live stage ledger Iris reports
 * through its heartbeats ("Reading slides", "Indexing transcript", ...), together with the stage
 * counter ("41/142"), the elapsed running time, and an inline retry marker after a transient retry.
 * This component owns the whole mapping from status to visuals, so richer observability (a details
 * card with stage timings, quality, and verification) can later attach here without touching the
 * management component.
 */
@Component({
    selector: 'jhi-ingestion-status-badge',
    templateUrl: './ingestion-status-badge.component.html',
    styleUrls: ['./ingestion-status-badge.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FaIconComponent, NgbTooltip, TumUiTagComponent, TranslateDirective, ArtemisTranslatePipe],
})
export class IngestionStatusBadgeComponent {
    protected readonly faSpinner = faSpinner;

    /** The unit's processing status; undefined while nothing is known yet. */
    status = input<LectureUnitProcessingStatus | undefined>(undefined);

    /** True when an IDLE (or unknown) unit will actually be picked up, so "Queued" is honest. */
    awaiting = input(false);

    /** Stage names Iris reports for the read-back audit at the end of a run. */
    private static readonly VERIFYING_STAGES = new Set(['audit']);

    /**
     * Maps a heartbeat stage name to its label, so the badge tells the reader what the pipeline is
     * actually doing rather than a generic "Indexing". Stages that already have a phase-level key
     * (embedding -> Indexing, audit -> Verifying) reuse it instead of duplicating the string.
     */
    private static readonly STAGE_LABEL_KEYS: Record<string, string> = {
        vision: 'artemisApp.attachmentVideoUnit.processing.stage.readingSlides',
        'segment-summaries': 'artemisApp.attachmentVideoUnit.processing.stage.summarizingSlides',
        embedding: 'artemisApp.attachmentVideoUnit.processingIngesting',
        'transcript-summaries': 'artemisApp.attachmentVideoUnit.processing.stage.summarizingTranscript',
        'transcript-embedding': 'artemisApp.attachmentVideoUnit.processing.stage.indexingTranscript',
        audit: 'artemisApp.attachmentVideoUnit.processingVerifying',
    };

    /** Ticks every second while running so the elapsed readout stays live under zoneless change detection. */
    private readonly now = signal<number>(Date.now());

    constructor() {
        effect((onCleanup) => {
            if (!this.baseRunning()) {
                return;
            }
            const handle = setInterval(() => this.now.set(Date.now()), ELAPSED_TICK_MS);
            onCleanup(() => clearInterval(handle));
        });
    }

    /**
     * The phase-derived state, before the liveness overlay. A running unit whose stream of lease
     * renewals has stopped is overlaid as 'lost' in {@link state}: liveness is computed from the row
     * at render time, never stored, so the badge cannot keep asserting "running" on stale evidence.
     */
    private baseState = computed<IngestionBadgeState | undefined>(() => {
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

    private baseRunning = computed(() => {
        const state = this.baseState();
        return state === 'transcribing' || state === 'indexing' || state === 'verifying';
    });

    /**
     * True when the run holds a worker lease whose renewals have stopped arriving. Only runs that
     * ever produced a lease take part ({@code lastHeartbeatAt} present) — a legacy run without
     * worker heartbeats stays under the server's timeout-based recovery and is never shown as lost.
     */
    lostContact = computed<boolean>(() => {
        if (!this.baseRunning()) {
            return false;
        }
        const status = this.status();
        if (!status?.lastHeartbeatAt || !status.receivedAt) {
            return false;
        }
        if (this.now() - status.receivedAt > LOST_CONTACT_AFTER_MS) {
            return true;
        }
        const serverHeartbeatMs = Date.parse(status.lastHeartbeatAt);
        return !Number.isNaN(serverHeartbeatMs) && this.now() - serverHeartbeatMs > LOST_CONTACT_SERVER_AGE_MS;
    });

    state = computed<IngestionBadgeState | undefined>(() => (this.lostContact() ? 'lost' : this.baseState()));

    isRunning = computed(() => {
        const state = this.state();
        return state === 'transcribing' || state === 'indexing' || state === 'verifying';
    });

    /** Live counter within the current stage, e.g. "41/142"; undefined when the stage reports none. */
    progressText = computed<string | undefined>(() => {
        const status = this.status();
        if ((!this.isRunning() && !this.lostContact()) || status?.stageProgress == null || !status.stageTotal) {
            return undefined;
        }
        return `${status.stageProgress}/${status.stageTotal}`;
    });

    /** Elapsed running time as a compact "1m 23s"; undefined unless the unit is running with a start time. */
    elapsed = computed<string | undefined>(() => {
        if (!this.isRunning()) {
            return undefined;
        }
        const startedAt = this.status()?.startedAt;
        if (!startedAt) {
            return undefined;
        }
        const startMs = Date.parse(startedAt);
        if (Number.isNaN(startMs)) {
            return undefined;
        }
        return IngestionStatusBadgeComponent.formatElapsed(this.now() - startMs);
    });

    /** How long ago the last lease renewal was received, e.g. "2m 4s"; only set in the lost state. */
    lastSeenAgo = computed<string | undefined>(() => {
        if (!this.lostContact()) {
            return undefined;
        }
        const receivedAt = this.status()?.receivedAt;
        if (!receivedAt) {
            return undefined;
        }
        return IngestionStatusBadgeComponent.formatElapsed(this.now() - receivedAt);
    });

    /** Number of automatic retries so far; drives the inline retry marker. */
    retryCount = computed<number>(() => this.status()?.retryCount ?? 0);

    /** Only surface the retry marker while it is actionable: a running or failed unit that has retried. */
    showRetry = computed<boolean>(() => this.retryCount() > 0 && (this.isRunning() || this.state() === 'lost' || this.state() === 'failed'));

    /**
     * Noun that follows the retry count, so the marker reads "3 retries" rather than "x3", which the eye
     * takes as a multiplier. Picked here rather than through a plural-suffixed key because the app runs
     * ngx-translate without a message-format compiler, so a "_one" key would never resolve.
     */
    retryLabelKey = computed<string>(() =>
        this.retryCount() === 1 ? 'artemisApp.attachmentVideoUnit.processing.retriedLabelOne' : 'artemisApp.attachmentVideoUnit.processing.retriedLabelOther',
    );

    tooltipKey = computed<string | undefined>(() => {
        const state = this.state();
        if (state === 'failed') {
            return this.status()?.errorKey ?? 'artemisApp.attachmentVideoUnit.processing.error.processingFailed';
        }
        if (state === 'skipped') {
            return 'artemisApp.attachmentVideoUnit.processingSkippedTooltip';
        }
        if (state === 'lost') {
            return 'artemisApp.attachmentVideoUnit.processing.lostContactTooltip';
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
            case 'verifying': {
                const stageName = this.status()?.stageName;
                return (stageName && IngestionStatusBadgeComponent.STAGE_LABEL_KEYS[stageName]) || 'artemisApp.attachmentVideoUnit.processingIngesting';
            }
            case 'lost':
                return 'artemisApp.attachmentVideoUnit.processingLostContact';
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

    severity = computed<TumUiTagSeverity>(() => {
        switch (this.state()) {
            case 'transcribing':
            case 'indexing':
            case 'verifying':
                return 'info';
            case 'lost':
                return 'warn';
            case 'done':
                return 'success';
            case 'failed':
                return 'danger';
            case 'queued':
            case 'skipped':
            default:
                return 'secondary';
        }
    });

    /**
     * Icon for the current state. Only a running unit carries one: the spinner is the single piece of
     * motion that says the pipeline is alive. A settled state is already carried by the tag's severity
     * colour and by its label, so a glyph there is decoration that costs width in a dense unit list.
     */
    icon = computed(() => (this.isRunning() ? this.faSpinner : undefined));

    /** Compact elapsed formatting: seconds under a minute, "1m 3s" under an hour, "1h 4m" beyond. */
    private static formatElapsed(ms: number): string {
        const totalSeconds = Math.max(0, Math.floor(ms / 1000));
        const hours = Math.floor(totalSeconds / 3600);
        const minutes = Math.floor((totalSeconds % 3600) / 60);
        const seconds = totalSeconds % 60;
        if (hours > 0) {
            return `${hours}h ${minutes}m`;
        }
        if (minutes > 0) {
            return `${minutes}m ${seconds}s`;
        }
        return `${seconds}s`;
    }
}
