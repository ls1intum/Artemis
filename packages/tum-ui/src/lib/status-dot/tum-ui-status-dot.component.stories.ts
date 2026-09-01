import { moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect } from 'storybook/test';

import { TumUiStatusDotComponent, TumUiStatusDotState } from './tum-ui-status-dot.component';

const states: TumUiStatusDotState[] = ['queued', 'running', 'success', 'warning', 'error', 'neutral', 'unknown'];

const stateWord: Record<TumUiStatusDotState, string> = {
    queued: 'Queued',
    running: 'Running',
    success: 'Succeeded',
    warning: 'Needs review',
    error: 'Failed',
    neutral: 'Not run',
    unknown: 'Status unavailable',
};

const meta = {
    title: 'Feedback/Status Dot',
    component: TumUiStatusDotComponent,
    args: {
        state: 'running',
        label: 'Running',
        showLabel: true,
        live: false,
    },
    argTypes: {
        state: {
            control: 'select',
            options: states,
        },
    },
} satisfies Meta<TumUiStatusDotComponent>;

export default meta;

type Story = StoryObj<TumUiStatusDotComponent>;

export const Default: Story = {};

/** Accepted but not started yet: a ring rather than a filled dot, so a screenshot separates it from `neutral`. */
export const Queued: Story = {
    args: {
        state: 'queued',
        label: 'Queued',
    },
};

export const Running: Story = {
    args: {
        state: 'running',
        label: 'Running',
    },
};

export const Success: Story = {
    args: {
        state: 'success',
        label: 'Succeeded',
    },
};

export const Warning: Story = {
    args: {
        state: 'warning',
        label: 'Needs review',
    },
};

export const Error: Story = {
    args: {
        state: 'error',
        label: 'Failed',
    },
};

/** No particular state — the thing exists and is simply not running. */
export const Neutral: Story = {
    args: {
        state: 'neutral',
        label: 'Not run',
    },
};

/**
 * The state could not be determined. It is not `neutral` ("no particular state") and not `queued` ("waiting"): the
 * broken ring says the reading itself failed, and it deliberately does not animate.
 */
export const Unknown: Story = {
    args: {
        state: 'unknown',
        label: 'Status unavailable',
    },
};

/** The hidden label stays in the accessibility tree, so the dot keeps its accessible name. */
export const WithoutLabel: Story = {
    args: {
        state: 'success',
        label: 'Succeeded',
        showLabel: false,
    },
};

/**
 * Review sheet: every state next to its word. A reviewer reads down the column to confirm that the state is legible
 * without the colour, in both themes. The three muted states differ in shape: `neutral` is filled, `queued` is a
 * ring, `unknown` is a broken ring.
 */
export const AllStates: Story = {
    decorators: [moduleMetadata({ imports: [TumUiStatusDotComponent] })],
    parameters: {
        layout: 'padded',
    },
    render: () => ({
        props: {
            rows: states.map((state) => ({ state, label: stateWord[state] })),
        },
        template: `
            <div class="tum-ui-story-legend">
                @for (row of rows; track row.state) {
                    <tum-ui-status-dot [state]="row.state" [label]="row.label" />
                    <code>{{ row.state }}</code>
                }
            </div>
        `,
    }),
};

/**
 * Measured proof of the two contracts a unit test in jsdom cannot see: the muted states differ in shape, and only the
 * in-flight states animate. jsdom evaluates neither `var()` substitution nor `prefers-reduced-motion`, so the check
 * runs in a real browser instead.
 */
export const StateShapes: Story = {
    tags: ['!dev', '!autodocs'],
    decorators: [moduleMetadata({ imports: [TumUiStatusDotComponent] })],
    parameters: {
        layout: 'padded',
    },
    render: () => ({
        props: {
            rows: states.map((state) => ({ state, label: stateWord[state] })),
        },
        template: `
            <div class="tum-ui-story-legend">
                @for (row of rows; track row.state) {
                    <tum-ui-status-dot [state]="row.state" [label]="row.label" [attr.data-testid]="'dot-' + row.state" />
                    <code>{{ row.state }}</code>
                }
            </div>
        `,
    }),
    play: async ({ canvas }) => {
        const indicator = (state: TumUiStatusDotState) => getComputedStyle(canvas.getByTestId(`dot-${state}`).querySelector('.tum-ui-status-dot-indicator')!);

        const neutral = indicator('neutral');
        const queued = indicator('queued');
        const unknown = indicator('unknown');

        await expect(neutral.backgroundColor, 'neutral is filled').not.toBe('rgba(0, 0, 0, 0)');
        await expect(queued.backgroundColor, 'queued is hollow').toBe('rgba(0, 0, 0, 0)');
        await expect(unknown.backgroundColor, 'unknown is hollow').toBe('rgba(0, 0, 0, 0)');
        await expect(queued.borderTopStyle, 'queued is a solid ring').toBe('solid');
        await expect(unknown.borderTopStyle, 'unknown is a broken ring').toBe('dashed');
        await expect(queued.borderTopColor, 'the ring carries the muted colour').toBe(neutral.backgroundColor);

        for (const state of states) {
            const animated = indicator(state).animationName !== 'none';
            await expect(animated, `${state} animates`).toBe(state === 'queued' || state === 'running');
        }
    },
};
