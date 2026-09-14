import { ChangeDetectionStrategy, Component, computed, signal } from '@angular/core';
import { moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect } from 'storybook/test';

import { TumUiButtonComponent } from '../button/tum-ui-button.component';
import { TumUiStepComponent, TumUiStepState } from './tum-ui-step.component';
import { TumUiStepperComponent, TumUiStepperOrientation } from './tum-ui-stepper.component';

interface StepperStoryStep {
    label: string;
    state: TumUiStepState;
    detail?: string;
}

type StepperStoryWidth = 'default' | 'narrow' | 'wide';

interface StepperStoryArgs {
    orientation: TumUiStepperOrientation;
    ariaLabel: string;
    /** Width of the presentation wrapper, so a story can show how labels behave in a narrow column. */
    width: StepperStoryWidth;
    steps: readonly StepperStoryStep[];
}

const orientations: TumUiStepperOrientation[] = ['vertical', 'horizontal'];
const states: TumUiStepState[] = ['pending', 'current', 'complete', 'failed', 'skipped'];

const widthClass: Record<StepperStoryWidth, string> = {
    default: 'tum-ui-story-ladder',
    narrow: 'tum-ui-story-ladder-narrow',
    wide: 'tum-ui-story-ladder-wide',
};

const stages = ['Prepare workspace', 'Plan', 'Build and test', 'Review and repair', 'Publish'];

function ladder(...ladderStates: TumUiStepState[]): StepperStoryStep[] {
    return stages.map((label, index) => ({ label, state: ladderStates[index] }));
}

const running = ladder('complete', 'current', 'pending', 'pending', 'pending');
running[1].detail = 'Drafting the plan.';

const meta = {
    title: 'Feedback/Stepper',
    component: TumUiStepperComponent,
    subcomponents: { Step: TumUiStepComponent },
    decorators: [
        moduleMetadata({
            imports: [TumUiStepComponent],
        }),
    ],
    args: {
        orientation: 'vertical',
        ariaLabel: 'Run stages',
        width: 'default',
        steps: running,
    },
    argTypes: {
        orientation: {
            control: 'select',
            options: orientations,
        },
        width: {
            control: 'select',
            options: Object.keys(widthClass),
        },
        steps: {
            control: 'object',
            description: `Stage list. Each entry carries one of: ${states.join(', ')}. A stage without a \`stateLabel\` is named by the package state word.`,
        },
    },
    parameters: {
        layout: 'padded',
    },
    render: ({ width, ...args }) => ({
        props: { ...args },
        template: `
            <div class="${widthClass[width]}">
                <tum-ui-stepper [orientation]="orientation" [ariaLabel]="ariaLabel">
                    @for (step of steps; track step.label) {
                        <tum-ui-step [state]="step.state" [label]="step.label">
                            @if (step.detail) {
                                {{ step.detail }}
                            }
                        </tum-ui-step>
                    }
                </tum-ui-stepper>
            </div>
        `,
    }),
} satisfies Meta<StepperStoryArgs>;

export default meta;

type Story = StoryObj<StepperStoryArgs>;

export const Default: Story = {};

/** Nothing has started yet, so no stage claims progress and every connector stays grey. */
export const AllPending: Story = {
    args: {
        steps: ladder('pending', 'pending', 'pending', 'pending', 'pending'),
    },
};

/**
 * The running stage carries the only spinner and the only `aria-current`. The travelled connector stops at that
 * stage: the segment leading into it is blue, everything after it is grey.
 */
export const Running: Story = {
    args: {
        steps: ladder('complete', 'complete', 'current', 'pending', 'pending'),
    },
};

/** A failed stage stops the ladder: the connector into it is red, and everything after it stays pending. */
export const Failed: Story = {
    args: {
        steps: ladder('complete', 'complete', 'failed', 'pending', 'pending'),
    },
};

/** A cancelled run has no running stage. Skipped stages carry the skip glyph and a dashed connector. */
export const Cancelled: Story = {
    args: {
        steps: ladder('complete', 'complete', 'skipped', 'skipped', 'skipped'),
    },
};

export const Complete: Story = {
    args: {
        steps: ladder('complete', 'complete', 'complete', 'complete', 'complete'),
    },
};

export const Horizontal: Story = {
    args: {
        orientation: 'horizontal',
        width: 'wide',
    },
};

/** Long labels in a narrow column wrap; a stage name is never truncated and never overflows the ladder. */
export const LongLabels: Story = {
    args: {
        width: 'narrow',
        steps: [
            { label: 'Arbeitsverzeichnis vorbereiten', state: 'complete' },
            { label: 'Aufgabenstellung und Bewertungsschema entwerfen', state: 'current', detail: 'Konsistenzprüfung der Teilschritte läuft.' },
            { label: 'Referenzlösung erstellen und Testdurchlauf ausführen', state: 'pending' },
            { label: 'Überprüfung und automatische Fehlerbehebung', state: 'pending' },
            { label: 'Ergebnis speichern', state: 'pending' },
        ],
    },
};

/**
 * A stage name that is more than a string: the projected `[tumUiStepLabel]` slot renders in the same line as the
 * `label` input, so a link or a count needs no second element and no wrapper around the step.
 */
export const ProjectedLabel: Story = {
    render: () => ({
        template: `
            <div class="tum-ui-story-ladder">
                <tum-ui-stepper ariaLabel="Run stages">
                    <tum-ui-step state="complete" label="Prepare workspace" />
                    <tum-ui-step state="failed">
                        <span tumUiStepLabel>Build and test — <a href="#log">open log</a></span>
                        2 of 18 checks failed.
                    </tum-ui-step>
                    <tum-ui-step label="Publish" />
                </tum-ui-stepper>
            </div>
        `,
    }),
};

const transitionTimeline: readonly TumUiStepState[][] = [
    ['pending', 'pending', 'pending'],
    ['current', 'pending', 'pending'],
    ['complete', 'current', 'pending'],
    ['complete', 'complete', 'current'],
    ['complete', 'complete', 'failed'],
];

const transitionLabels = ['Prepare workspace', 'Build and test', 'Publish'];

/** Story-only driver: it holds the ladder state so a reader can step through the transitions by hand. */
@Component({
    selector: 'tum-ui-stepper-transition-demo',
    imports: [TumUiButtonComponent, TumUiStepComponent, TumUiStepperComponent],
    template: `
        <div class="tum-ui-story-stack tum-ui-story-ladder">
            <div class="tum-ui-story-actions">
                <tum-ui-button size="small" [disabled]="atEnd()" (clicked)="advance()">Advance</tum-ui-button>
                <tum-ui-button size="small" severity="secondary" variant="outlined" [disabled]="atStart()" (clicked)="reset()">Reset</tum-ui-button>
            </div>
            <tum-ui-stepper ariaLabel="Run stages">
                @for (label of labels; track label; let index = $index) {
                    <tum-ui-step [state]="ladder()[index]" [label]="label" />
                }
            </tum-ui-stepper>
        </div>
    `,
    changeDetection: ChangeDetectionStrategy.OnPush,
})
class StepperTransitionDemoComponent {
    protected readonly labels = transitionLabels;
    protected readonly step = signal(0);
    protected readonly ladder = computed(() => transitionTimeline[this.step()]);
    protected readonly atStart = computed(() => this.step() === 0);
    protected readonly atEnd = computed(() => this.step() === transitionTimeline.length - 1);

    protected advance(): void {
        this.step.update((step) => Math.min(step + 1, transitionTimeline.length - 1));
    }

    protected reset(): void {
        this.step.set(0);
    }
}

/**
 * The transitions themselves: advancing the ladder moves a stage from pending through current and complete into
 * failed, and every marker, label and connector fades between the two colours instead of snapping. Under
 * `prefers-reduced-motion: reduce` the same change applies instantly.
 */
export const StateTransitions: Story = {
    decorators: [moduleMetadata({ imports: [StepperTransitionDemoComponent] })],
    render: () => ({
        template: '<tum-ui-stepper-transition-demo />',
    }),
    play: async ({ canvas, userEvent }) => {
        const stages = () => canvas.getAllByRole('listitem').map((stage) => stage.textContent?.replace(/\s+/g, ' ').trim());
        const current = () => canvas.getAllByRole('listitem').filter((stage) => stage.getAttribute('aria-current') === 'step');

        await expect(stages()[0]).toBe('Prepare workspace Not started');
        await expect(current()).toHaveLength(0);

        const advance = canvas.getByRole('button', { name: 'Advance' });
        await userEvent.click(advance);
        await expect(stages()[0]).toBe('Prepare workspace In progress');
        await expect(current()).toHaveLength(1);

        await userEvent.click(advance);
        await expect(stages()[0]).toBe('Prepare workspace Done');
        await expect(stages()[1]).toBe('Build and test In progress');

        await userEvent.click(advance);
        await userEvent.click(advance);
        await expect(stages()[2]).toBe('Publish Failed');
        await expect(current()).toHaveLength(0);
    },
};

/**
 * Measured proof that the connector is centred on the marker, in both orientations. The unit test cannot do this:
 * jsdom performs no layout, so every rectangle it reports is empty. Here the marker and its connector are measured
 * in a real browser and their centres must agree within half a pixel.
 */
export const ConnectorAlignment: Story = {
    tags: ['!dev', '!autodocs'],
    render: () => ({
        template: `
            <div class="tum-ui-story-stack tum-ui-story-ladder-wide">
                <tum-ui-stepper ariaLabel="Vertical alignment probe" data-testid="vertical-ladder">
                    <tum-ui-step state="complete" label="Prepare workspace">A detail line that makes this stage taller than its marker.</tum-ui-step>
                    <tum-ui-step state="current" label="Build and test" />
                </tum-ui-stepper>
                <tum-ui-stepper orientation="horizontal" ariaLabel="Horizontal alignment probe" data-testid="horizontal-ladder">
                    <tum-ui-step state="complete" label="Prepare workspace">A detail line that makes this stage taller than its marker.</tum-ui-step>
                    <tum-ui-step state="current" label="Build and test" />
                </tum-ui-stepper>
            </div>
        `,
    }),
    play: async ({ canvas }) => {
        for (const [ladder, axis] of [
            ['vertical-ladder', 'x'],
            ['horizontal-ladder', 'y'],
        ] as const) {
            const step = canvas.getByTestId(ladder).querySelector('tum-ui-step')!;
            const marker = step.querySelector('.tum-ui-step-marker')!.getBoundingClientRect();
            const connector = step.querySelector('.tum-ui-step-connector')!.getBoundingClientRect();

            const markerCentre = axis === 'x' ? marker.left + marker.width / 2 : marker.top + marker.height / 2;
            const connectorCentre = axis === 'x' ? connector.left + connector.width / 2 : connector.top + connector.height / 2;

            await expect(connector.width, `${ladder} connector is drawn`).toBeGreaterThan(0);
            await expect(connector.height, `${ladder} connector is drawn`).toBeGreaterThan(0);
            await expect(Math.abs(connectorCentre - markerCentre), `${ladder} connector centre`).toBeLessThanOrEqual(0.5);
        }

        // The connector of the leading step reaches the marker of the step it leads into, whatever the content height.
        const [first, second] = Array.from(canvas.getByTestId('vertical-ladder').querySelectorAll('tum-ui-step'));
        const connector = first.querySelector('.tum-ui-step-connector')!.getBoundingClientRect();
        const nextMarker = second.querySelector('.tum-ui-step-marker')!.getBoundingClientRect();
        await expect(Math.abs(connector.bottom - nextMarker.top), 'connector meets the next marker').toBeLessThanOrEqual(0.5);

        // Nothing is drawn out of the stage that ends the ladder.
        await expect(second.querySelector('.tum-ui-step-connector')!.getBoundingClientRect().height).toBe(0);
    },
};

// --- Non-text contrast, measured -----------------------------------------------------------------------------
// WCAG 1.4.11 asks for 3:1 on a graphical object a reader needs in order to understand the content. A ladder's
// markers and rails are exactly that, so they are measured here, once, in both themes.
const CONTRAST_MINIMUM = 3;

function relativeLuminance(colour: string): number {
    const [red, green, blue] = colour
        .match(/[\d.]+/g)!
        .slice(0, 3)
        .map((channel) => Number(channel) / 255);
    const linear = (channel: number) => (channel <= 0.03928 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4);
    return 0.2126 * linear(red) + 0.7152 * linear(green) + 0.0722 * linear(blue);
}

function contrastRatio(foreground: string, background: string): number {
    const [lighter, darker] = [relativeLuminance(foreground), relativeLuminance(background)].sort((left, right) => right - left);
    return (lighter + 0.05) / (darker + 0.05);
}

/**
 * Every marker ring and every connector, measured against the surface. The stage that used to fail is `pending`:
 * drawn in the border colour it measured 1.23:1 in light and 1.24:1 in dark, so the part of the ladder that says
 * "not started" was the part nobody could see. It now takes the muted colour, like the ink beside it.
 */
export const NonTextContrast: Story = {
    tags: ['!dev', '!autodocs'],
    parameters: {
        layout: 'padded',
    },
    render: () => ({
        template: `
            <tum-ui-stepper ariaLabel="Contrast probe" data-testid="contrast-ladder">
                <tum-ui-step state="complete" label="Complete" />
                <tum-ui-step state="failed" label="Failed" />
                <tum-ui-step state="skipped" label="Skipped" />
                <tum-ui-step state="current" label="Current" />
                <tum-ui-step state="pending" label="Pending" />
            </tum-ui-stepper>
        `,
    }),
    play: async ({ canvas }) => {
        const surface = getComputedStyle(document.body).backgroundColor;
        const steps = Array.from(canvas.getByTestId('contrast-ladder').querySelectorAll('tum-ui-step'));

        for (const step of steps) {
            const state = step.getAttribute('data-state');
            const marker = getComputedStyle(step.querySelector('.tum-ui-step-marker')!);
            await expect(contrastRatio(marker.borderTopColor, surface), `${state} marker ring reaches 3:1`).toBeGreaterThanOrEqual(CONTRAST_MINIMUM);
            await expect(contrastRatio(marker.color, surface), `${state} marker glyph reaches 3:1`).toBeGreaterThanOrEqual(CONTRAST_MINIMUM);

            const connector = step.querySelector('.tum-ui-step-connector')!;
            if (connector.getBoundingClientRect().height > 0) {
                await expect(contrastRatio(getComputedStyle(connector).borderInlineStartColor, surface), `${state} connector reaches 3:1`).toBeGreaterThanOrEqual(CONTRAST_MINIMUM);
            }
        }
    },
};
