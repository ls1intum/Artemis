import { moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';

import { TumUiStatusDotComponent, TumUiStatusDotState } from './tum-ui-status-dot.component';

const states: TumUiStatusDotState[] = ['queued', 'running', 'success', 'warning', 'error', 'neutral'];

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

export const Neutral: Story = {
    args: {
        state: 'neutral',
        label: 'Unknown',
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
 * without the colour, in both themes.
 */
export const AllStates: Story = {
    decorators: [moduleMetadata({ imports: [TumUiStatusDotComponent] })],
    parameters: {
        layout: 'padded',
    },
    render: () => ({
        props: {
            rows: [
                { state: 'queued', label: 'Queued' },
                { state: 'running', label: 'Running' },
                { state: 'success', label: 'Succeeded' },
                { state: 'warning', label: 'Needs review' },
                { state: 'error', label: 'Failed' },
                { state: 'neutral', label: 'Unknown' },
            ],
        },
        template: `
            <div style="display: grid; grid-template-columns: auto auto; justify-content: start; column-gap: 2rem; row-gap: 0.75rem;">
                @for (row of rows; track row.state) {
                    <tum-ui-status-dot [state]="row.state" [label]="row.label" />
                    <code style="font-size: 0.875rem; color: var(--tumaet-ui-muted-color);">{{ row.state }}</code>
                }
            </div>
        `,
    }),
};
