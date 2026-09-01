import { ChangeDetectionStrategy, Component, computed, effect, input } from '@angular/core';
import { Subject } from 'rxjs';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faSpinner } from '@fortawesome/free-solid-svg-icons';

import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { HyperionActivityView, formatDuration } from 'app/hyperion/exercise-generation/model/hyperion-generation-activity';
import { elapsedSecondsSince, serverTimeSignal } from 'app/localci/hyperion-generation-job.utils';

/** The liveness line, with its duration already worded so the template binds a string. */
interface LivenessLine {
    labelKey: string;
    duration: string;
    waiting: boolean;
}

/**
 * What the agent is doing inside the stage that is running, rendered under that stage in the ladder.
 *
 * The reason this exists is silence: consecutive model calls in a real run landed one to three minutes apart with
 * nothing said in between, and a panel that says nothing for three minutes is indistinguishable from a stuck one.
 * So the first thing here is a clock that keeps moving, and only then the counters and the recent messages.
 */
@Component({
    selector: 'jhi-hyperion-run-activity',
    templateUrl: './hyperion-run-activity.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [ArtemisTranslatePipe, TranslateDirective, FaIconComponent],
})
export class HyperionRunActivityComponent {
    readonly view = input.required<HyperionActivityView>();
    /** `compact` drops the recent-activity list, which does not fit the code editor's bottom panel. */
    readonly density = input<'full' | 'compact'>('full');

    /** Emits once the run is over, which is what stops the clock from ticking for the rest of the session. */
    private readonly runEnded = new Subject<void>();
    private readonly now = serverTimeSignal(this.runEnded);

    protected readonly counters = computed(() => this.view().counters);
    protected readonly recent = computed(() => (this.density() === 'full' ? this.view().recent : []));

    protected readonly liveness = computed<LivenessLine | undefined>(() => {
        const liveness = this.view().liveness;
        if (!liveness) {
            return undefined;
        }
        const duration = formatDuration(elapsedSecondsSince(liveness.since, this.now()));
        return {
            labelKey: liveness.waitingOnModel ? 'artemisApp.hyperion.generation.activity.thinking' : 'artemisApp.hyperion.generation.activity.lastUpdate',
            duration,
            waiting: liveness.waitingOnModel,
        };
    });

    protected readonly faSpinner = faSpinner;

    constructor() {
        effect(() => {
            if (this.view().ended) {
                this.runEnded.next();
            }
        });
    }
}
