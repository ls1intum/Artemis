import { Injectable, Signal, computed, signal } from '@angular/core';
import type { TumUiStepperOrientation } from './tum-ui-stepper.component';

/**
 * Publishes the stepper's layout to the steps it projects.
 *
 * A step is declared in the consumer's template, so it cannot read the stepper's `orientation` input directly. The
 * stepper registers that input here once, and each step reads the shared signal back. A step used without a stepper
 * injects nothing and falls back to the vertical default.
 */
@Injectable()
export class TumUiStepperService {
    private readonly source = signal<Signal<TumUiStepperOrientation>>(signal<TumUiStepperOrientation>('vertical'));

    /** Layout the enclosing stepper currently renders. */
    readonly orientation = computed<TumUiStepperOrientation>(() => this.source()());

    register(orientation: Signal<TumUiStepperOrientation>): void {
        this.source.set(orientation);
    }
}
