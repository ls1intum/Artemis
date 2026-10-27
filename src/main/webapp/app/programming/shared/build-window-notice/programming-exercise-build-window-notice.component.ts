import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TumAetUiMessageComponent } from '@tumaet/ui-angular';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { BuildWindowPhase, getBuildTimeWindow, injectBuildWindowPhase } from 'app/programming/shared/utils/build-time-window.utils';

/**
 * Tells students why a push may not be built: the exercise has a build time window, and pushes outside of it are saved but not built.
 * Shows nothing for exercises without a window. It only explains, the server stays authoritative.
 */
@Component({
    selector: 'jhi-programming-exercise-build-window-notice',
    templateUrl: './programming-exercise-build-window-notice.component.html',
    imports: [TumAetUiMessageComponent, ArtemisTranslatePipe, ArtemisDatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProgrammingExerciseBuildWindowNoticeComponent {
    readonly exercise = input.required<Exercise>();
    /** Pushes in practice mode are always built, so there is nothing to explain. */
    readonly practiceMode = input(false);

    protected readonly BuildWindowPhase = BuildWindowPhase;

    protected readonly buildTimeWindow = computed(() => (this.practiceMode() ? undefined : getBuildTimeWindow(this.exercise())));
    protected readonly phase = injectBuildWindowPhase(this.buildTimeWindow);
}
