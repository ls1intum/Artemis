import { Injectable, Signal, computed, signal } from '@angular/core';
import type { TumAetUiStepperOrientation } from './tumaet-ui-stepper.component';

/**
 * Publishes the stepper's layout to the steps it projects.
 *
 * A step is declared in the consumer's template, so it cannot read the stepper's `orientation` input directly. The
 * stepper registers that input here once, and each step reads the shared signal back. A step used without a stepper
 * injects nothing and falls back to the vertical default.
 */
@Injectable()
export class TumAetUiStepperService {
    private readonly source = signal<Signal<TumAetUiStepperOrientation>>(signal<TumAetUiStepperOrientation>('vertical'));

    /** Layout the enclosing stepper currently renders. */
    readonly orientation = computed<TumAetUiStepperOrientation>(() => this.source()());

    register(orientation: Signal<TumAetUiStepperOrientation>): void {
        this.source.set(orientation);
    }
}
