import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { TumUiCardTitleComponent, TumUiMessageComponent, TumUiMessageSeverity, TumUiPanelComponent, TumUiStatusDotComponent, TumUiStatusDotState } from '@tumaet/ui-angular';

import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { HyperionGenerationEvent } from 'app/hyperion/exercise-generation/hyperion-generation-stream.model';

/** One verification gate, reported with a state word and a sentence so the result never rests on colour alone. */
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
    /** Whether the run ended without saving and its work really is kept, so there is something to inspect. */
    retained: boolean;
    /** Whether the run ended without saving and kept nothing, which the instructor must be told rather than left to discover. */
    nothingRetained: boolean;
    /** Raw English server prose. Never the primary message; only ever shown under technical details. */
    serverMessages: readonly string[];
    /** The models the run used. Diagnostic rather than a figure, so it belongs in the log and not on the spend surface. */
    models: readonly string[];
    events: readonly HyperionGenerationEvent[];
}

/** A check reads as a state word and a sentence; the dot is what makes the state visible without being the only signal. */
interface OutcomeCheckLine {
    labelKey: string;
    dotState: TumUiStatusDotState;
    stateKey: string;
}

/**
 * How a run ended, in the instructor's language. This is the answer the page exists to give once a run is over, so it
 * carries the largest element on the page and it sits above every figure describing what the run consumed.
 *
 * Two statements, never one: what happened, and what happened to the user's work. A failure that kept a candidate and
 * a failure that kept nothing are different facts, and the second one has to be said rather than left to be discovered.
 *
 * The server's own English prose and the raw transcript stay behind one disclosure, whose header carries the count so
 * that nothing about the state of the run is hidden by collapsing it.
 */
@Component({
    selector: 'jhi-hyperion-run-outcome',
    templateUrl: './hyperion-run-outcome.component.html',
    styleUrl: './hyperion-run-outcome.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [ArtemisTranslatePipe, TranslateDirective, TumUiCardTitleComponent, TumUiMessageComponent, TumUiPanelComponent, TumUiStatusDotComponent],
})
export class HyperionRunOutcomeComponent {
    private readonly translateService = inject(TranslateService);

    readonly view = input.required<HyperionRunOutcomeView>();

    protected readonly checks = computed<OutcomeCheckLine[]>(() =>
        this.view().checks.map((check) => ({
            labelKey: check.labelKey,
            dotState: check.passed ? 'success' : 'danger',
            // The state reaches assistive technology as a word, never as a colour or a glyph.
            stateKey: check.passed ? 'artemisApp.hyperion.generation.verdict.passed' : 'artemisApp.hyperion.generation.verdict.failed',
        })),
    );

    protected readonly hasTechnicalDetails = computed(() => this.view().serverMessages.length > 0 || this.view().events.length > 0);

    /**
     * The disclosure's header, carrying how many server messages are inside it.
     *
     * A collapsed region must not be the only place a statement about the run exists, so the count travels out onto the
     * header the reader can see without opening anything.
     */
    protected readonly technicalDetailsHeader = computed(() => {
        const count = this.view().serverMessages.length;
        const label = this.translateService.instant('artemisApp.hyperion.generation.outcome.technicalDetails');
        return count > 0 ? `${label} · ${this.translateService.instant('artemisApp.hyperion.generation.outcome.serverMessageCount', { count })}` : label;
    });

    protected readonly models = computed(() => {
        const models = this.view().models;
        return models.length > 0 ? models.join(', ') : undefined;
    });
}
