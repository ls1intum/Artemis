import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faCheck, faMinus, faXmark } from '@fortawesome/free-solid-svg-icons';
import { TumUiStepperService } from './tum-ui-stepper.service';

export type TumUiStepState = 'pending' | 'current' | 'complete' | 'failed' | 'skipped';

/** States that convey their marker with an icon. `current` renders a running indicator, `pending` a hollow circle. */
const STEP_STATE_ICON: Record<TumUiStepState, IconProp | undefined> = {
    pending: undefined,
    current: undefined,
    complete: faCheck,
    failed: faXmark,
    skipped: faMinus,
};

const STEP_MARKER_BASE =
    'tum-ui-step-marker tum:flex tum:size-6 tum:shrink-0 tum:items-center tum:justify-center tum:rounded-full tum:border-2 tum:bg-content-background tum:text-xs';

const STEP_MARKER_STATE: Record<TumUiStepState, string> = {
    pending: 'tum:text-muted tum:border-border',
    current: 'tum:text-primary tum:border-primary',
    complete: 'tum:text-state-success tum:border-state-success',
    failed: 'tum:text-state-danger tum:border-state-danger',
    skipped: 'tum:text-muted tum:border-border',
};

/** The connector leaving a step is only "travelled" once the step is reached. */
const STEP_CONNECTOR_REACHED = 'tum:border-primary';
const STEP_CONNECTOR_PENDING = 'tum:border-border';

const STEP_BASE = 'tum-ui-step tum:relative tum:flex';
const STEP_VERTICAL = 'tum:flex-row tum:gap-3 tum:border-s-2 tum:pb-6';
const STEP_HORIZONTAL = 'tum:min-w-40 tum:flex-1 tum:flex-col tum:gap-2 tum:border-t-2 tum:pe-4';

/**
 * One stage of a {@link TumUiStepperComponent} ladder.
 *
 * The step is a status display, not a control: it is neither clickable nor focusable. Its state reaches assistive
 * technology as text through `stateLabel`, never through the marker colour alone. Project detail content in the
 * default slot; it renders under the label in every state.
 */
@Component({
    selector: 'tum-ui-step',
    templateUrl: './tum-ui-step.component.html',
    styleUrl: './tum-ui-step.component.scss',
    imports: [FaIconComponent],
    host: {
        role: 'listitem',
        '[class]': 'hostClasses()',
        '[attr.data-state]': 'state()',
        '[attr.data-orientation]': 'orientation()',
        '[attr.aria-current]': "state() === 'current' ? 'step' : null",
        '[attr.aria-disabled]': "isInactive() ? 'true' : null",
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiStepComponent {
    private readonly stepper = inject(TumUiStepperService, { optional: true });

    /** Progress state of this stage. */
    readonly state = input<TumUiStepState>('pending');

    /** Visible stage name. */
    readonly label = input.required<string>();

    /** Overrides the marker icon, including the running indicator of a `current` step. */
    readonly icon = input<IconProp>();

    /**
     * Translated state word appended to the label for assistive technology only, for example "Running" or "Failed".
     * Supply it so the state is not conveyed by the marker colour alone; without it no hidden text is rendered.
     */
    readonly stateLabel = input<string>();

    protected readonly orientation = computed(() => this.stepper?.orientation() ?? 'vertical');
    protected readonly isInactive = computed(() => this.state() === 'pending' || this.state() === 'skipped');
    protected readonly markerIcon = computed(() => this.icon() ?? STEP_STATE_ICON[this.state()]);
    protected readonly isRunning = computed(() => !this.markerIcon() && this.state() === 'current');

    protected readonly hostClasses = computed(() => {
        const layout = this.orientation() === 'horizontal' ? STEP_HORIZONTAL : STEP_VERTICAL;
        const reached = this.state() === 'complete' || this.state() === 'current';
        return `${STEP_BASE} ${layout} ${reached ? STEP_CONNECTOR_REACHED : STEP_CONNECTOR_PENDING}`;
    });

    protected readonly markerClasses = computed(() => `${STEP_MARKER_BASE} ${STEP_MARKER_STATE[this.state()]}`);

    protected readonly labelClasses = computed(() => `tum-ui-step-label tum:font-medium tum:break-words ${this.state() === 'skipped' ? 'tum:text-muted' : ''}`.trimEnd());
}
