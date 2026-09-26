import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { TumAetUiStepperService } from './tumaet-ui-stepper.service';

export type TumAetUiStepperOrientation = 'vertical' | 'horizontal';

const STEPPER_LIST_BASE = 'tumaet-ui-stepper-list tumaet:flex tumaet:m-0 tumaet:list-none tumaet:p-0';

const STEPPER_LIST_ORIENTATION: Record<TumAetUiStepperOrientation, string> = {
    vertical: 'tumaet:flex-col',
    horizontal: 'tumaet:flex-row tumaet:flex-wrap tumaet:gap-y-6',
};

/**
 * Progress ladder for a multi-stage operation.
 *
 * The stepper is a status display, not a navigation control: its steps are neither clickable nor focusable. Project
 * `tumaet-ui-step` children in the order they run.
 *
 * The list keeps an explicit `role="list"`, because a flex `<ol>` without markers loses its list semantics in some
 * browsers and every step depends on that list to carry its `role="listitem"`.
 */
@Component({
    selector: 'tumaet-ui-stepper',
    templateUrl: './tumaet-ui-stepper.component.html',
    host: {
        '[attr.data-slot]': '"stepper"',
        class: 'tumaet-ui-stepper tumaet:block tumaet:text-text',
        '[attr.data-orientation]': 'orientation()',
    },
    providers: [TumAetUiStepperService],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiStepperComponent {
    private readonly stepperService = inject(TumAetUiStepperService);

    /** Layout direction of the ladder. */
    readonly orientation = input<TumAetUiStepperOrientation>('vertical');

    /** Accessible name of the step list. */
    readonly ariaLabel = input<string>();

    constructor() {
        this.stepperService.register(this.orientation);
    }

    protected readonly listClasses = computed(() => `${STEPPER_LIST_BASE} ${STEPPER_LIST_ORIENTATION[this.orientation()]}`);
}
