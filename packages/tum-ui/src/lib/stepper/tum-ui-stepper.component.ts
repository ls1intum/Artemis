import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { TumUiStepperService } from './tum-ui-stepper.service';

export type TumUiStepperOrientation = 'vertical' | 'horizontal';

const STEPPER_LIST_BASE = 'tum-ui-stepper-list tum:flex tum:m-0 tum:list-none tum:p-0';

const STEPPER_LIST_ORIENTATION: Record<TumUiStepperOrientation, string> = {
    vertical: 'tum:flex-col',
    horizontal: 'tum:flex-row tum:flex-wrap tum:gap-y-6',
};

/**
 * Progress ladder for a multi-stage operation.
 *
 * The stepper is a status display, not a navigation control: its steps are neither clickable nor focusable. Project
 * `tum-ui-step` children in the order they run.
 */
@Component({
    selector: 'tum-ui-stepper',
    templateUrl: './tum-ui-stepper.component.html',
    host: {
        class: 'tum-ui-stepper tum:block tum:text-text',
        '[attr.data-orientation]': 'orientation()',
    },
    providers: [TumUiStepperService],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiStepperComponent {
    private readonly stepperService = inject(TumUiStepperService);

    /** Layout direction of the ladder. */
    readonly orientation = input<TumUiStepperOrientation>('vertical');

    /** Accessible name of the step list. */
    readonly ariaLabel = input<string>();

    constructor() {
        this.stepperService.register(this.orientation);
    }

    protected readonly listClasses = computed(() => `${STEPPER_LIST_BASE} ${STEPPER_LIST_ORIENTATION[this.orientation()]}`);
}
