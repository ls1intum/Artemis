import { ChangeDetectionStrategy, Component, computed, effect, input, output } from '@angular/core';
import { Subject } from 'rxjs';
import { TumUiButtonComponent, TumUiStatusDotComponent, TumUiStatusDotState } from '@tumaet/ui-angular';

import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { HyperionActivityView, RECENT_ACTIVITY_LIMIT, formatDuration, isStalled } from 'app/hyperion/exercise-generation/model/hyperion-generation-activity';
import { elapsedSecondsSince, serverTimeSignal } from 'app/localci/hyperion-generation-job.utils';

/** The liveness line, with its duration already worded so the template binds a string. */
interface LivenessLine {
    labelKey: string;
    duration: string;
    waiting: boolean;
    /** The silence has lasted longer than a pause, which changes the tone, the words and what is offered. */
    stalled: boolean;
    dotState: TumUiStatusDotState;
}

/**
 * What the agent is doing inside the stage that is running, rendered under that stage in the ladder.
 *
 * The reason this exists is silence: consecutive model calls in a real run landed one to three minutes apart with
 * nothing said in between, and a panel that says nothing for three minutes is indistinguishable from a stuck one.
 * So the first thing here is a clock that keeps moving, and only then the counters and the recent messages.
 *
 * Past the silence threshold the surface stops calling the run "working" and starts calling it stuck: the tone
 * changes, the silence is stated in words rather than left as a muted number that only grows, and Cancel is promoted
 * to where the reader is already looking. Minute 14 of a hung provider call must not look like minute 1.
 */
@Component({
    selector: 'jhi-hyperion-run-activity',
    templateUrl: './hyperion-run-activity.component.html',
    styleUrl: './hyperion-run-activity.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [ArtemisTranslatePipe, TranslateDirective, TumUiButtonComponent, TumUiStatusDotComponent],
})
export class HyperionRunActivityComponent {
    readonly view = input.required<HyperionActivityView>();
    /** `compact` drops the recent-activity list, which does not fit the code editor's bottom panel. */
    readonly density = input<'full' | 'compact'>('full');
    /** Offered inside the stage row once the run stalls, which is the moment the escape action stops being secondary. */
    readonly cancelAvailable = input(false);
    readonly cancelPending = input(false);
    /**
     * Whether the page can still reach the server.
     *
     * The stall wording promises the run is "still connected", and that promise is only honest while it is true. When
     * the status check itself has failed the page reports a lost connection instead, and this line says nothing about
     * the connection at all.
     */
    readonly connected = input(true);

    readonly cancelRequested = output<void>();

    /** Emits once the run is over, which is what stops the clock from ticking for the rest of the session. */
    private readonly runEnded = new Subject<void>();
    private readonly now = serverTimeSignal(this.runEnded);

    protected readonly counters = computed(() => this.view().counters);
    protected readonly latestFile = computed(() => this.view().latestFile);
    protected readonly recent = computed(() => (this.density() === 'full' ? this.view().recent : []));
    /** Reserves the log's full height so a message arriving never reflows the text being read above it. */
    protected readonly recentLimit = RECENT_ACTIVITY_LIMIT;

    protected readonly liveness = computed<LivenessLine | undefined>(() => {
        const liveness = this.view().liveness;
        if (!liveness) {
            return undefined;
        }
        const now = this.now();
        const duration = formatDuration(elapsedSecondsSince(liveness.since, now));
        const stalled = isStalled(liveness, now);
        return {
            labelKey: stalled
                ? this.connected()
                    ? 'artemisApp.hyperion.generation.activity.stalled'
                    : 'artemisApp.hyperion.generation.activity.stalledOffline'
                : liveness.waitingOnModel
                  ? 'artemisApp.hyperion.generation.activity.thinking'
                  : 'artemisApp.hyperion.generation.activity.lastUpdate',
            duration,
            waiting: liveness.waitingOnModel,
            stalled,
            // A stalled run must not keep pulsing as if it were busy; the warning dot is deliberately motionless.
            dotState: stalled ? 'warning' : 'running',
        };
    });

    /** Cancel is only promoted here once the run has actually stalled; while it is working it stays in the header. */
    protected readonly promoteCancel = computed(() => this.cancelAvailable() && this.liveness()?.stalled === true);

    /**
     * The class an arriving message fades in with, or nothing at all when the surface is not live.
     *
     * `animate.enter` fires for the initial render of a `@for` too, so an unguarded binding fades in every row at once
     * when a finished run is opened - motion the user did not cause, reporting an arrival that did not happen.
     */
    protected readonly rowEnterClass = computed(() => (this.view().ended ? '' : 'hyperion-activity-row-entering'));

    constructor() {
        effect(() => {
            if (this.view().ended) {
                this.runEnded.next();
            }
        });
    }
}
