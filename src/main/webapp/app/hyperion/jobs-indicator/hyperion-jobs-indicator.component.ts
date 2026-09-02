import { ChangeDetectionStrategy, Component, computed, effect, inject, signal, viewChild } from '@angular/core';
import { RouterLink } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faXmark } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import {
    TumUiButtonDirective,
    TumUiEmptyComponent,
    TumUiEmptyDescriptionComponent,
    TumUiEmptyHeaderComponent,
    TumUiEmptyTitleComponent,
    TumUiItemContentComponent,
    TumUiItemDescriptionComponent,
    TumUiItemDirective,
    TumUiItemGroupDirective,
    TumUiItemMediaComponent,
    TumUiItemTitleComponent,
    TumUiMessageComponent,
    TumUiPopoverComponent,
    TumUiPopoverTriggerDirective,
    TumUiStatusDotComponent,
    TumUiStatusDotState,
    TumUiTagComponent,
    TumUiTooltipDirective,
} from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import {
    HyperionJobEntry,
    HyperionJobIndicatorState,
    HyperionJobRegistryService,
    HyperionJobStatus,
    isTerminalHyperionJobStatus,
} from 'app/hyperion/exercise-generation/state/hyperion-job-registry.service';

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
    failed: 'danger',
    cancelled: 'neutral',
    unknown: 'neutral',
};

const JOBS_KEY_PREFIX = 'artemisApp.hyperion.jobs.';

/** How often the elapsed clock is re-read while the tray is open. Minute resolution needs nothing finer. */
const TRAY_CLOCK_INTERVAL_MS = 30_000;

/** One tracked run, with every string it renders already resolved so the template holds no logic. */
export interface HyperionJobRow {
    jobId: string;
    title: string;
    status: HyperionJobStatus;
    dotState: TumUiStatusDotState;
    /** The status word, which is also the dot's accessible name. */
    statusLabel: string;
    /** `running for 7 min` / `ran for 21 min`, or absent when no duration can honestly be derived. */
    duration?: string;
    routerLink: (string | number)[];
}

/**
 * Navbar tray answering one question: do any of my generation runs need me?
 *
 * Renders nothing at all while there is nothing to report, so the navbar stays quiet for everyone who is not currently
 * generating an exercise. When it does appear, the aggregate state is in the visible label and in the button's
 * accessible name, never in the dot's colour alone.
 *
 * States it has: **empty** — the component is absent entirely. **Running** — the summary counts what is in flight and
 * the rows count up. **Attention / success** — the summary says how many finished runs are waiting to be opened.
 * **Load failed** — every entry already known stays on screen and one message with a retry is added beneath the
 * title, because a failed reconciliation says nothing about the runs themselves.
 */
@Component({
    selector: 'jhi-hyperion-jobs-indicator',
    templateUrl: './hyperion-jobs-indicator.component.html',
    styleUrl: './hyperion-jobs-indicator.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        RouterLink,
        FaIconComponent,
        ArtemisTranslatePipe,
        TranslateDirective,
        TumUiButtonDirective,
        TumUiEmptyComponent,
        TumUiEmptyDescriptionComponent,
        TumUiEmptyHeaderComponent,
        TumUiEmptyTitleComponent,
        TumUiItemContentComponent,
        TumUiItemDescriptionComponent,
        TumUiItemDirective,
        TumUiItemGroupDirective,
        TumUiItemMediaComponent,
        TumUiItemTitleComponent,
        TumUiMessageComponent,
        TumUiPopoverComponent,
        TumUiPopoverTriggerDirective,
        TumUiStatusDotComponent,
        TumUiTagComponent,
        TumUiTooltipDirective,
    ],
})
export class HyperionJobsIndicatorComponent {
    private readonly registry = inject(HyperionJobRegistryService);
    private readonly translateService = inject(TranslateService);

    protected readonly faXmark = faXmark;

    protected readonly entries = this.registry.entries;
    protected readonly unseenCount = this.registry.unseenCount;
    protected readonly indicatorState = this.registry.indicatorState;
    protected readonly loadFailed = this.registry.loadFailed;

    protected readonly indicatorDotState = computed(() => INDICATOR_DOT_STATE[this.indicatorState()]);

    private readonly popover = viewChild(TumUiPopoverComponent);

    /** Whether the tray is on screen, which is the only time the elapsed clock is worth running. */
    private readonly trayOpen = signal(false);
    private readonly now = signal(Date.now());

    private readonly languageChange = toSignal(this.translateService.onLangChange, { initialValue: undefined });
    /** Read by {@link translate}, so a language switch rebuilds every resolved string rather than freezing it. */
    private readonly locale = computed(() => {
        this.languageChange();
        return this.translateService.getCurrentLang() ?? 'en';
    });

    private readonly unseenEntries = computed(() => this.entries().filter((entry) => isTerminalHyperionJobStatus(entry.status) && !entry.seen));

    /** Runs that have not reached a terminal status. */
    private readonly runningCount = computed(() => this.entries().filter((entry) => !isTerminalHyperionJobStatus(entry.status)).length);
    /** Finished runs the user has not opened that ended in anything but a clean save. */
    private readonly attentionCount = computed(() => this.unseenEntries().filter((entry) => entry.status !== 'saved').length);
    /** Finished runs the user has not opened that were saved cleanly. */
    private readonly finishedCount = computed(() => this.unseenEntries().filter((entry) => entry.status === 'saved').length);

    /**
     * What the trigger says out loud, e.g. `2 running · 1 to check`.
     *
     * Every phrase is written so it reads correctly for one as well as for many, in both languages: this translation
     * layer has no plural forms, and "1 runs" is worse than a phrasing that never needs one.
     */
    protected readonly summaryParts = computed(() => {
        const parts: string[] = [];
        const say = (key: string, count: number) => {
            if (count > 0) {
                parts.push(this.translate(JOBS_KEY_PREFIX + key, { count }));
            }
        };
        say('summaryRunning', this.runningCount());
        say('summaryAttention', this.attentionCount());
        say('summaryFinished', this.finishedCount());
        return parts;
    });

    /** The noun stays the fallback: the indicator is debounced, so it can briefly outlive the entries that justified it. */
    protected readonly summaryLabel = computed(() => {
        const parts = this.summaryParts();
        return parts.length > 0 ? parts.join(' · ') : this.translate(JOBS_KEY_PREFIX + 'label');
    });

    /** The state goes into the accessible name, not only into the dot: a failed run and a running one must not sound alike. */
    protected readonly triggerAriaLabel = computed(() => `${this.translate(JOBS_KEY_PREFIX + 'ariaLabel')}: ${this.summaryLabel()}`);

    /** The aggregate state as a word, so the dot never carries it by colour alone. */
    protected readonly indicatorStateLabel = computed(() => this.translate(`${JOBS_KEY_PREFIX}state.${this.indicatorState()}`));

    protected readonly rows = computed<HyperionJobRow[]>(() => {
        const now = this.now();
        return this.entries().map((entry) => ({
            jobId: entry.jobId,
            title: entry.exerciseTitle,
            status: entry.status,
            dotState: ENTRY_DOT_STATE[entry.status] ?? 'neutral',
            statusLabel: this.translate(`artemisApp.hyperion.generation.status.${entry.status}`),
            duration: this.durationOf(entry, now),
            routerLink: ['/course-management', entry.courseId, 'programming-exercises', entry.exerciseId, 'generation'],
        }));
    });

    constructor() {
        effect((onCleanup) => {
            // A closed tray is worth no timer, and a tray with nothing in flight has nothing left to count. Minute
            // resolution also keeps this out of the "reprints once a second" class, which must never animate or announce.
            if (!this.trayOpen() || this.runningCount() === 0) {
                return;
            }
            const handle = setInterval(() => this.now.set(Date.now()), TRAY_CLOCK_INTERVAL_MS);
            onCleanup(() => clearInterval(handle));
        });
    }

    /** Opening a run counts as having seen it, so the badge clears. */
    protected openEntry(jobId: string): void {
        this.registry.markSeen(jobId);
        this.popover()?.close();
    }

    protected dismiss(jobId: string): void {
        this.registry.dismiss(jobId);
    }

    protected retry(): void {
        this.registry.refresh();
    }

    /** Re-reads the clock as the tray opens, so a tray opened an hour later does not first paint an hour-old figure. */
    protected onTrayOpenChange(open: boolean): void {
        this.trayOpen.set(open);
        if (open) {
            this.now.set(Date.now());
        }
    }

    /** Resolves a key now and again whenever the language changes; the `locale()` read is what makes callers reactive. */
    private translate(key: string, params?: Record<string, unknown>): string {
        this.locale();
        return this.translateService.instant(key, params);
    }

    /**
     * How long a run has been going, or how long it took.
     *
     * A finished run is measured against the server's own last event rather than against the clock, so its figure
     * freezes where the run stopped instead of counting on for ever. When the registry never saw the run end — it was
     * reconciled long after the fact, or the entry predates that bookkeeping — no duration is claimed at all.
     */
    private durationOf(entry: HyperionJobEntry, now: number): string | undefined {
        const startedAt = Date.parse(entry.startedAt);
        if (!Number.isFinite(startedAt)) {
            return undefined;
        }
        const running = !isTerminalHyperionJobStatus(entry.status);
        const endedAt = running ? now : Date.parse(entry.endedAt ?? '');
        if (!Number.isFinite(endedAt) || endedAt < startedAt) {
            return undefined;
        }
        const minutes = Math.floor((endedAt - startedAt) / 60_000);
        const suffix = minutes < 1 ? 'UnderMinute' : 'Minutes';
        return this.translate(`${JOBS_KEY_PREFIX}${running ? 'running' : 'ran'}${suffix}`, { minutes });
    }
}
