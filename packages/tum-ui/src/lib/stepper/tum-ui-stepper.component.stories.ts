import { moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';

import { TumUiStepComponent, TumUiStepState } from './tum-ui-step.component';
import { TumUiStepperComponent, TumUiStepperOrientation } from './tum-ui-stepper.component';

interface StepperStoryStep {
    label: string;
    state: TumUiStepState;
    stateLabel: string;
    detail?: string;
}

interface StepperStoryArgs {
    orientation: TumUiStepperOrientation;
    ariaLabel: string;
    /** Width of the presentation wrapper, so a story can show how labels behave in a narrow column. */
    width: string;
    steps: readonly StepperStoryStep[];
}

const orientations: TumUiStepperOrientation[] = ['vertical', 'horizontal'];
const states: TumUiStepState[] = ['pending', 'current', 'complete', 'failed', 'skipped'];

const stages = ['Prepare workspace', 'Design', 'Build and test', 'Review and repair', 'Save'];

const stateWord: Record<TumUiStepState, string> = {
    pending: 'Pending',
    current: 'Running',
    complete: 'Complete',
    failed: 'Failed',
    skipped: 'Skipped',
};

function ladder(...ladderStates: TumUiStepState[]): StepperStoryStep[] {
    return stages.map((label, index) => ({ label, state: ladderStates[index], stateLabel: stateWord[ladderStates[index]] }));
}

const running = ladder('complete', 'current', 'pending', 'pending', 'pending');
running[1].detail = 'Drafting the problem statement.';

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
        ariaLabel: 'Generation progress',
        width: '30rem',
        steps: running,
    },
    argTypes: {
        orientation: {
            control: 'select',
            options: orientations,
        },
        steps: {
            control: 'object',
            description: `Stage list. Each entry carries one of: ${states.join(', ')}.`,
        },
    },
    parameters: {
        layout: 'padded',
    },
    render: ({ width, ...args }) => ({
        props: { ...args, width },
        template: `
            <tum-ui-stepper [orientation]="orientation" [ariaLabel]="ariaLabel" [style.width]="width" style="max-width: 100%;">
                @for (step of steps; track step.label) {
                    <tum-ui-step [state]="step.state" [label]="step.label" [stateLabel]="step.stateLabel">
                        @if (step.detail) {
                            {{ step.detail }}
                        }
                    </tum-ui-step>
                }
            </tum-ui-stepper>
        `,
    }),
} satisfies Meta<StepperStoryArgs>;

export default meta;

type Story = StoryObj<StepperStoryArgs>;

export const Default: Story = {};

/** Nothing has started yet, so no stage claims progress. */
export const AllPending: Story = {
    args: {
        steps: ladder('pending', 'pending', 'pending', 'pending', 'pending'),
    },
};

/** The running stage carries the only spinner and the only `aria-current`. */
export const Running: Story = {
    args: {
        steps: ladder('complete', 'complete', 'current', 'pending', 'pending'),
    },
};

/** A failed stage stops the ladder: everything after it stays pending. */
export const Failed: Story = {
    args: {
        steps: ladder('complete', 'complete', 'failed', 'pending', 'pending'),
    },
};

/** A cancelled run has no running stage, so the ladder shows no spinner at all. */
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
        width: '52rem',
    },
};

/** Long labels in a narrow column wrap; a stage name is never truncated and never overflows the ladder. */
export const LongLabels: Story = {
    args: {
        width: '17rem',
        steps: [
            { label: 'Arbeitsverzeichnis vorbereiten', state: 'complete', stateLabel: 'Complete' },
            { label: 'Aufgabenstellung und Bewertungsschema entwerfen', state: 'current', stateLabel: 'Running', detail: 'Konsistenzprüfung der Teilaufgaben läuft.' },
            { label: 'Referenzlösung erstellen und Testdurchlauf ausführen', state: 'pending', stateLabel: 'Pending' },
            { label: 'Überprüfung und automatische Fehlerbehebung', state: 'pending', stateLabel: 'Pending' },
            { label: 'Ergebnis speichern', state: 'pending', stateLabel: 'Pending' },
        ],
    },
};
