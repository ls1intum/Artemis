import type { Meta, StoryObj } from '@storybook/angular-vite';
import { fn } from 'storybook/test';

import { TumUiRadioButtonClickEvent, TumUiRadioButtonComponent } from './tum-ui-radio-button.component';

interface RadioOption {
    label: string;
    value: string;
}

interface RadioButtonStoryArgs {
    legend: string;
    options: readonly RadioOption[];
    selected: string | undefined;
    disabled: boolean;
    onClick: (event: TumUiRadioButtonClickEvent) => void;
}

const OPTIONS: readonly RadioOption[] = [
    { label: 'Daily', value: 'daily' },
    { label: 'Weekly', value: 'weekly' },
    { label: 'Never', value: 'never' },
];

const meta = {
    title: 'Forms/Radio Button',
    component: TumUiRadioButtonComponent,
    args: {
        legend: 'Email summary',
        options: OPTIONS,
        selected: 'weekly',
        disabled: false,
        onClick: fn(),
    },
    render: (args) => ({
        props: args,
        template: `
            <fieldset>
                <legend>{{ legend }}</legend>
                @for (option of options; track option.value) {
                    <label [for]="'summary-' + option.value">
                        <tum-ui-radio-button
                            [inputId]="'summary-' + option.value"
                            name="summary"
                            [value]="option.value"
                            [modelValue]="selected"
                            [disabled]="disabled"
                            (onClick)="selected = $event.value; onClick($event)"
                        />
                        {{ option.label }}
                    </label>
                }
            </fieldset>
        `,
    }),
} satisfies Meta<RadioButtonStoryArgs>;

export default meta;

type Story = StoryObj<RadioButtonStoryArgs>;

export const Default: Story = {};

export const Disabled: Story = {
    args: {
        disabled: true,
    },
};
