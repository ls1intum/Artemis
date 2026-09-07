import { FormsModule } from '@angular/forms';
import { moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect, fn } from 'storybook/test';

import { formStoryDecorator } from '../../../.storybook/story-decorators';
import { TumUiSelectButtonComponent, TumUiSelectButtonSize } from './tum-ui-select-button.component';

interface IntervalOption {
    label: string;
    value: string;
    abbreviation: string;
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
    { label: 'Day', value: 'day', abbreviation: 'D' },
    { label: 'Week', value: 'week', abbreviation: 'W' },
    { label: 'Month', value: 'month', abbreviation: 'M' },
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
    argTypes: {
        changed: { control: false },
        options: { control: 'object' },
        selected: {
            control: 'inline-radio',
            options: [undefined, ...OPTIONS.map((option) => option.value)],
        },
        size: {
            control: 'inline-radio',
            options: [undefined, 'small', 'large'],
        },
    },
    decorators: [
        formStoryDecorator,
        moduleMetadata({
            imports: [FormsModule],
        }),
    ],
    render: (args) => {
        return {
            props: { ...args },
            template: `
                <span id="interval-label" class="tum-ui-story-label">{{ label }}</span>
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
        };
    },
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
    tags: ['!dev', '!autodocs'],
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

export const CustomItemTemplate: Story = {
    render: (args) => {
        return {
            props: { ...args },
            template: `
                <span id="compact-interval-label" class="tum-ui-story-label">{{ label }}</span>
                <tum-ui-select-button
                    aria-labelledby="compact-interval-label"
                    [options]="options"
                    optionLabel="label"
                    optionValue="value"
                    [itemTemplate]="item"
                    [(ngModel)]="selected"
                    (changed)="changed($event)"
                />
                <ng-template #item let-option>
                    <span [attr.aria-label]="option.label">{{ option.abbreviation }}</span>
                </ng-template>
            `,
        };
    },
};
