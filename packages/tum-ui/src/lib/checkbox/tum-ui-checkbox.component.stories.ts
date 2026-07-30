import { argsToTemplate } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { useArgs } from 'storybook/preview-api';
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
    render: function Render(args) {
        const [{ checked }, updateArgs] = useArgs<CheckboxStoryArgs>();
        return {
            props: {
                ...args,
                checked,
                change: (event: TumUiCheckboxChangeEvent) => {
                    updateArgs({ checked: event.checked });
                    args.onChange(event);
                },
            },
            template: `
                <label for="terms">
                    <tum-ui-checkbox
                        inputId="terms"
                        name="terms"
                        [checked]="checked"
                        ${argsToTemplate(args, { exclude: ['checked', 'label', 'onChange'] })}
                        (onChange)="change($event)"
                    />
                    {{ label }}
                </label>
            `,
        };
    },
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
