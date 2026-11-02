import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCheck, faForwardStep, faXmark } from '@fortawesome/free-solid-svg-icons';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { HyperionStepState } from 'app/hyperion/exercise-generation/model/hyperion-generation-stages';

/** One non-interactive generation stage, including its connector and accessible state. */
@Component({
    selector: 'jhi-hyperion-stage',
    templateUrl: './hyperion-stage.component.html',
    styleUrl: './hyperion-stage.component.scss',
    imports: [FaIconComponent, ArtemisTranslatePipe],
    host: {
        role: 'listitem',
        '[attr.data-state]': 'state()',
        '[attr.aria-current]': "state() === 'current' ? 'step' : null",
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HyperionStageComponent {
    readonly state = input.required<HyperionStepState>();
    readonly label = input.required<string>();
    protected readonly markerIcon = computed(() => ({ complete: faCheck, failed: faXmark, skipped: faForwardStep, current: undefined, pending: undefined })[this.state()]);
    protected readonly isRunning = computed(() => this.state() === 'current');
    protected readonly stateLabelKey = computed(() => `global.stepState.${this.state()}`);
}
