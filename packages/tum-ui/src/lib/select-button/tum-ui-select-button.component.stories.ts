import { FormsModule } from '@angular/forms';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect, fn } from 'storybook/test';

import { TumUiSelectButtonComponent, TumUiSelectButtonSize } from './tum-ui-select-button.component';

interface IntervalOption {
    label: string;
    value: string;
}

interface SelectButtonStoryArgs {
    label: string;
    options: readonly IntervalOption[];
    selected: string | undefined;
    size: TumUiSelectButtonSize | undefined;
    allowEmpty: boolean;
    disabled: boolean;
    changed: (value: string | undefined) => void;
}

const OPTIONS: readonly IntervalOption[] = [
    { label: 'Day', value: 'day' },
    { label: 'Week', value: 'week' },
    { label: 'Month', value: 'month' },
];

const meta = {
    title: 'Forms/Select Button',
    component: TumUiSelectButtonComponent,
    args: {
        label: 'Reporting interval',
        options: OPTIONS,
        selected: 'day',
        size: undefined,
        allowEmpty: false,
        disabled: false,
        changed: fn(),
    },
    render: (args) => ({
        props: args,
        moduleMetadata: {
            imports: [FormsModule],
        },
        template: `
            <span id="interval-label">{{ label }}</span>
            <tum-ui-select-button
                aria-labelledby="interval-label"
                [options]="options"
                optionLabel="label"
                optionValue="value"
                [size]="size"
                [allowEmpty]="allowEmpty"
                [disabled]="disabled"
                [(ngModel)]="selected"
                (changed)="changed($event)"
            />
        `,
    }),
} satisfies Meta<SelectButtonStoryArgs>;

export default meta;

type Story = StoryObj<SelectButtonStoryArgs>;

export const Default: Story = {};

export const Small: Story = {
    args: {
        size: 'small',
    },
};

export const Disabled: Story = {
    args: {
        selected: 'week',
        disabled: true,
    },
};

export const KeyboardInteraction: Story = {
    play: async ({ args, canvas, userEvent }) => {
        const day = canvas.getByRole('button', { name: 'Day' });
        const week = canvas.getByRole('button', { name: 'Week' });

        await userEvent.tab();
        await expect(day).toHaveFocus();
        await expect(day).toHaveAttribute('aria-pressed', 'true');
        await userEvent.tab();
        await expect(week).toHaveFocus();
        await userEvent.keyboard(' ');
        await expect(day).toHaveAttribute('aria-pressed', 'false');
        await expect(week).toHaveAttribute('aria-pressed', 'true');
        await expect(args.changed).toHaveBeenCalledWith('week');
    },
};
