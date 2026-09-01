import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCircleCheck, faCircleXmark } from '@fortawesome/free-solid-svg-icons';
import { TumUiMessageComponent, TumUiMessageSeverity } from '@tumaet/ui-angular';

import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { HyperionGenerationEvent } from 'app/hyperion/exercise-generation/hyperion-generation-stream.model';

/** One verification gate, reported with an icon and a sentence so the result never rests on colour alone. */
export interface HyperionRunOutcomeCheck {
    labelKey: string;
    passed: boolean;
}

/** Everything the outcome block renders, resolved by the run page so this component only formats. */
export interface HyperionRunOutcomeView {
    severity: TumUiMessageSeverity;
    titleKey: string;
    bodyKey: string;
    /** Interpolation for the body; the saved outcome names the number of tests. */
    bodyParams?: Record<string, number>;
    /** Translated cause of a failure or cancellation, from the terminal event's termination reason. */
    terminationReasonKey?: string;
    checks: readonly HyperionRunOutcomeCheck[];
    /** The translated "n tests" line under the checks, or `undefined` when no verdict was reported. */
    testCountKey?: string;
    testCountParams?: Record<string, number>;
    /** Whether the run ended without saving, so the work is only kept for inspection. */
    retained: boolean;
    /** Raw English server prose. Never the primary message; only ever shown under technical details. */
    serverMessages: readonly string[];
    events: readonly HyperionGenerationEvent[];
}

/**
 * How a run ended, in the instructor's language.
 *
 * Replaces the live area the moment a terminal event arrives, so the page never reads as still working on something
 * that has already stopped.
 */
@Component({
    selector: 'jhi-hyperion-run-outcome',
    templateUrl: './hyperion-run-outcome.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [ArtemisTranslatePipe, TranslateDirective, FaIconComponent, TumUiMessageComponent],
})
export class HyperionRunOutcomeComponent {
    readonly view = input.required<HyperionRunOutcomeView>();

    protected readonly technicalDetailsExpanded = signal(false);

    protected readonly hasChecks = computed(() => this.view().checks.length > 0);
    protected readonly hasTechnicalDetails = computed(() => this.view().serverMessages.length > 0 || this.view().events.length > 0);

    protected readonly faCircleCheck = faCircleCheck;
    protected readonly faCircleXmark = faCircleXmark;

    protected toggleTechnicalDetails(): void {
        this.technicalDetailsExpanded.update((expanded) => !expanded);
    }
}
