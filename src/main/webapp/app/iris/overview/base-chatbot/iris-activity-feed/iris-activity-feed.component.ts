import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCaretRight, faCheck, faCircleNotch, faCircleXmark } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { getCurrentLocaleSignal } from 'app/foundation/util/global.utils';
import { IrisActivityItem, IrisActivityState } from 'app/iris/shared/entities/iris-activity.model';
import { cloneWith } from 'app/foundation/util/deep-clone.util';

export type IrisActivityFeedMode = 'live' | 'trail';

// Number of decimals the summed duration in the summary is rendered with.
const ACTIVITY_DURATION_DECIMALS = 1;
// Shortest summed duration (in s) still worth showing: anything below this rounds away to "0.0s" at one decimal.
const MIN_DISPLAYED_ACTIVITY_DURATION_SECONDS = 0.5 * 10 ** -ACTIVITY_DURATION_DECIMALS;

interface IrisActivityViewItem extends IrisActivityItem {
    label: string;
    durationLabel?: string;
    stateClass: string;
    icon: typeof faCircleNotch;
    spin: boolean;
    /** Accessible label (name + detail + state + duration) exposed on the stepper node via aria-label. */
    tooltip: string;
}

export function prettifyActivityName(name: string): string {
    const label = name.replace(/_/g, ' ').trim();
    if (!label) {
        return '';
    }
    return `${label.charAt(0).toUpperCase()}${label.slice(1)}`;
}

/**
 * One group of Iris steps, drawn as a quiet one-line summary that expands to a list.
 * Live, the summary names the step that is running; afterwards it counts the steps.
 */
@Component({
    selector: 'jhi-iris-activity-feed',
    templateUrl: './iris-activity-feed.component.html',
    styleUrl: './iris-activity-feed.component.scss',
    imports: [FaIconComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IrisActivityFeedComponent {
    private readonly translateService = inject(TranslateService);
    private readonly currentLocale = getCurrentLocaleSignal(this.translateService);

    protected readonly faCaretRight = faCaretRight;
    protected readonly faCircleNotch = faCircleNotch;

    readonly activities = input<IrisActivityItem[]>([]);
    readonly mode = input<IrisActivityFeedMode>('live');

    protected readonly viewItems = computed<IrisActivityViewItem[]>(() => {
        this.currentLocale();
        return this.activities().map((activity) => {
            const label = this.translateActivityName(activity.name);
            const durationLabel = this.formatDuration(activity);
            // Accessible name reads name (+ detail) + state (+ duration), matching the visible label order.
            // The state is otherwise conveyed only by the node icon, which is invisible to screen readers.
            const namePart = activity.detail ? `${label} · ${activity.detail}` : label;
            const parts = [namePart, this.translateActivityState(activity.state)];
            if (durationLabel) {
                parts.push(durationLabel);
            }
            return cloneWith(activity, {
                label,
                durationLabel,
                stateClass: activity.state.toLowerCase(),
                icon: this.iconFor(activity.state),
                spin: activity.state === IrisActivityState.RUNNING,
                tooltip: parts.join(' · '),
            });
        });
    });

    /** Only the live group names a running step; a saved group may hold one that never finished. */
    protected readonly runningItem = computed(() => (this.mode() === 'live' ? this.viewItems().find((activity) => activity.state === IrisActivityState.RUNNING) : undefined));

    protected readonly summary = computed(() => {
        this.currentLocale();
        const activities = this.activities();
        const totalDurationSeconds = activities.reduce((sum, activity) => sum + (activity.durationMillis ?? 0), 0) / 1000;
        // A single activity reads as "1 tool", more than one as "n tools", so the plural form picks the key.
        const pluralSuffix = activities.length === 1 ? 'Singular' : 'Plural';
        // Durations that round away to "0.0s" add noise rather than information, so the time is omitted entirely.
        if (totalDurationSeconds < MIN_DISPLAYED_ACTIVITY_DURATION_SECONDS) {
            return this.translateService.instant(`artemisApp.iris.activities.trailSummaryWithoutDuration${pluralSuffix}`, { count: activities.length });
        }
        return this.translateService.instant(`artemisApp.iris.activities.trailSummary${pluralSuffix}`, {
            count: activities.length,
            duration: totalDurationSeconds.toFixed(ACTIVITY_DURATION_DECIMALS),
        });
    });

    protected readonly feedAriaLabel = computed(() => {
        this.currentLocale();
        return this.translateService.instant(this.mode() === 'live' ? 'artemisApp.iris.activities.liveLabel' : 'artemisApp.iris.activities.trailLabel');
    });

    private translateActivityName(name: string): string {
        const key = `artemisApp.iris.activities.${name}`;
        const translated = this.translateService.instant(key);
        if (typeof translated === 'string' && translated !== key && !translated.startsWith('translation-not-found[')) {
            return translated;
        }
        return prettifyActivityName(name);
    }

    private translateActivityState(state: IrisActivityState): string {
        const key = `artemisApp.iris.activities.states.${state.toLowerCase()}`;
        const translated = this.translateService.instant(key);
        if (typeof translated === 'string' && translated !== key && !translated.startsWith('translation-not-found[')) {
            return translated;
        }
        return prettifyActivityName(state.toLowerCase());
    }

    private formatDuration(activity: IrisActivityItem): string | undefined {
        if (activity.state !== IrisActivityState.FINISHED || activity.durationMillis === undefined) {
            return undefined;
        }
        // Sub-100ms durations would render as a meaningless "0.0s" badge.
        if (activity.durationMillis < 100) {
            return undefined;
        }
        return `${(activity.durationMillis / 1000).toFixed(1)}s`;
    }

    private iconFor(state: IrisActivityState): typeof faCircleNotch {
        if (state === IrisActivityState.FINISHED) {
            return faCheck;
        }
        if (state === IrisActivityState.FAILED) {
            return faCircleXmark;
        }
        return faCircleNotch;
    }
}
