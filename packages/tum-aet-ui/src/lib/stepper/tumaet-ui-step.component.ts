import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faCheck, faForwardStep, faXmark } from '@fortawesome/free-solid-svg-icons';
import { TUM_AET_UI_TRANSLATOR, TumAetUiTranslationKey } from '../i18n/tumaet-ui-translations';
import { TumAetUiStepperService } from './tumaet-ui-stepper.service';

export type TumAetUiStepState = 'pending' | 'current' | 'complete' | 'failed' | 'skipped';

/** States that convey their marker with an icon. `current` renders a running indicator, `pending` a hollow circle. */
const STEP_STATE_ICON: Record<TumAetUiStepState, IconProp | undefined> = {
    pending: undefined,
    current: undefined,
    complete: faCheck,
    failed: faXmark,
    skipped: faForwardStep,
};

/** Package wording used when the consumer supplies no `stateLabel`, so the state is never colour-only. */
const STEP_STATE_KEY: Record<TumAetUiStepState, TumAetUiTranslationKey> = {
    pending: 'tumAetUi.step.pending',
    current: 'tumAetUi.step.current',
    complete: 'tumAetUi.step.complete',
    failed: 'tumAetUi.step.failed',
    skipped: 'tumAetUi.step.skipped',
};

/**
 * One stage of a {@link TumAetUiStepperComponent} ladder.
 *
 * The step is a status display, not a control: it is neither clickable nor focusable. Its state reaches assistive
 * technology as a hidden word next to the label, never through the marker colour alone. Project detail content in the
 * default slot; it renders under the label in every state.
 *
 * The connector between two markers is drawn by the earlier step but always carries the state of the step it leads
 * into, so a colour never runs past the stage that earned it.
 */
@Component({
    selector: 'tumaet-ui-step',
    templateUrl: './tumaet-ui-step.component.html',
    styleUrl: './tumaet-ui-step.component.scss',
    imports: [FaIconComponent],
    host: {
        '[attr.data-slot]': '"step"',
        role: 'listitem',
        class: 'tumaet-ui-step',
        '[attr.data-state]': 'state()',
        '[attr.data-orientation]': 'orientation()',
        '[attr.aria-current]': "state() === 'current' ? 'step' : null",
        '[attr.aria-disabled]': "isInactive() ? 'true' : null",
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiStepComponent {
    private readonly stepper = inject(TumAetUiStepperService, { optional: true });
    private readonly translator = inject(TUM_AET_UI_TRANSLATOR);

    /** Progress state of this stage. */
    readonly state = input<TumAetUiStepState>('pending');

    /** Visible stage name. Omit it when projecting a `[tumAetUiStepLabel]` slot instead; both render in the same line. */
    readonly label = input<string>();

    /** Overrides the marker icon, including the running indicator of a `current` step. */
    readonly icon = input<IconProp>();

    /**
     * Translated state word rendered next to the label for assistive technology only, for example "Running".
     * It defaults to the package wording for `state`; override it when the domain has a better word.
     */
    readonly stateLabel = input<string>();

    protected readonly orientation = computed(() => this.stepper?.orientation() ?? 'vertical');
    protected readonly isInactive = computed(() => this.state() === 'pending' || this.state() === 'skipped');
    protected readonly markerIcon = computed(() => this.icon() ?? STEP_STATE_ICON[this.state()]);
    protected readonly isRunning = computed(() => !this.markerIcon() && this.state() === 'current');

    protected readonly stateWord = computed(() => {
        const supplied = this.stateLabel()?.trim();
        if (supplied) {
            return supplied;
        }
        this.translator.translationChanges?.();
        return this.translator.translate(STEP_STATE_KEY[this.state()]);
    });
}
