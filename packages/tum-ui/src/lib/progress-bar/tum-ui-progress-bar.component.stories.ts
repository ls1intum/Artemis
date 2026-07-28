import { argsToTemplate } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';

import { TumUiProgressBarComponent } from './tum-ui-progress-bar.component';

const values = [0, 25, 50, 75, 100];

const meta = {
    title: 'Feedback/Progress Bar',
    component: TumUiProgressBarComponent,
    args: {
        ariaLabel: 'Course completion',
        value: 60,
        showValue: true,
        unit: '%',
    },
    argTypes: {
        color: {
            control: false,
        },
        value: {
            control: {
                type: 'range',
                min: 0,
                max: 100,
                step: 1,
            },
        },
    },
    render: (args) => ({
        props: args,
        template: `
            <div style="width: min(28rem, 80vw);">
                <tum-ui-progress-bar ${argsToTemplate(args)} />
            </div>
        `,
    }),
} satisfies Meta<TumUiProgressBarComponent>;

export default meta;

type Story = StoryObj<TumUiProgressBarComponent>;

export const Default: Story = {};

export const Values: Story = {
    render: ({ showValue, unit }) => ({
        props: { showValue, unit, values },
        template: `
            <div style="display: grid; width: min(28rem, 80vw); gap: 0.75rem;">
                @for (value of values; track value) {
                    <tum-ui-progress-bar [value]="value" [showValue]="showValue" [unit]="unit" [ariaLabel]="'Course completion: ' + value + unit" />
                }
            </div>
        `,
    }),
    parameters: {
        controls: {
            include: ['showValue', 'unit'],
        },
    },
};
