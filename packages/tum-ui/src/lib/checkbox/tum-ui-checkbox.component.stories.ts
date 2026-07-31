import { argsToTemplate } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { fn } from 'storybook/test';

import { inlineControlStoryDecorator } from '../../../.storybook/story-decorators';
import { TumUiCheckboxChangeEvent, TumUiCheckboxComponent } from './tum-ui-checkbox.component';

interface CheckboxStoryArgs {
    label: string;
    checked: boolean;
    disabled: boolean;
    changed: (event: TumUiCheckboxChangeEvent) => void;
}

const meta = {
    title: 'Forms/Checkbox',
    component: TumUiCheckboxComponent,
    args: {
        label: 'Accept the terms and conditions',
        checked: false,
        disabled: false,
        changed: fn(),
    },
    argTypes: {
        changed: { control: false },
    },
    decorators: [inlineControlStoryDecorator],
    render: (args) => {
        return {
            props: { ...args },
            template: `
                <label for="terms">
                    <tum-ui-checkbox
                        inputId="terms"
                        name="terms"
                        [checked]="checked"
                        ${argsToTemplate(args, { exclude: ['checked', 'label', 'changed'] })}
                        (changed)="checked = $event.checked; changed($event)"
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
