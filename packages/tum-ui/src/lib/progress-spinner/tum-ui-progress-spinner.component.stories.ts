import type { Meta, StoryObj } from '@storybook/angular-vite';

import { TumUiProgressSpinnerComponent } from './tum-ui-progress-spinner.component';

const meta = {
    title: 'Feedback/Progress Spinner',
    component: TumUiProgressSpinnerComponent,
    args: {
        ariaLabel: 'Loading course data',
        animationDuration: '2s',
        fill: 'none',
        strokeWidth: 2,
    },
} satisfies Meta<TumUiProgressSpinnerComponent>;

export default meta;

type Story = StoryObj<TumUiProgressSpinnerComponent>;

export const Default: Story = {};

export const Variations: Story = {
    render: ({ fill }) => ({
        props: { fill },
        template: `
            <div style="display: flex; align-items: center; gap: 2rem;">
                <tum-ui-progress-spinner ariaLabel="Default spinner" [fill]="fill" />
                <tum-ui-progress-spinner ariaLabel="Fast spinner" [fill]="fill" animationDuration="0.75s" />
                <tum-ui-progress-spinner ariaLabel="Thick spinner" [fill]="fill" [strokeWidth]="4" />
            </div>
        `,
    }),
    parameters: {
        controls: {
            include: ['fill'],
        },
    },
};
