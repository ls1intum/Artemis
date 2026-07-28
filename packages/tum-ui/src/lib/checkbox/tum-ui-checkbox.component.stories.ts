import type { Meta, StoryObj } from '@storybook/angular-vite';
import { fn } from 'storybook/test';

import { TumUiCheckboxChangeEvent, TumUiCheckboxComponent } from './tum-ui-checkbox.component';

interface CheckboxStoryArgs {
    label: string;
    checked: boolean;
    disabled: boolean;
    onChange: (event: TumUiCheckboxChangeEvent) => void;
}

const meta = {
    title: 'Forms/Checkbox',
    component: TumUiCheckboxComponent,
    args: {
        label: 'Accept the terms and conditions',
        checked: false,
        disabled: false,
        onChange: fn(),
    },
    render: (args) => ({
        props: args,
        template: `
            <label for="terms">
                <tum-ui-checkbox
                    inputId="terms"
                    name="terms"
                    [checked]="checked"
                    [disabled]="disabled"
                    (onChange)="onChange($event)"
                />
                {{ label }}
            </label>
        `,
    }),
} satisfies Meta<CheckboxStoryArgs>;

export default meta;

type Story = StoryObj<CheckboxStoryArgs>;

export const Default: Story = {};

export const Checked: Story = {
    args: {
        checked: true,
    },
};

export const Disabled: Story = {
    args: {
        checked: true,
        disabled: true,
    },
};
