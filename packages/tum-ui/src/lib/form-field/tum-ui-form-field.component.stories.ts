import { argsToTemplate, moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { formStoryDecorator } from '../../../.storybook/story-decorators';
import { TumUiFormFieldComponent } from './tum-ui-form-field.component';
import { TumUiInputDirective } from '../input/tum-ui-input.directive';
import { TumUiSelectComponent } from '../select/tum-ui-select.component';

interface FormFieldStoryArgs {
    label: string;
    required: boolean;
    hint?: string;
    invalid: boolean;
    error?: string;
}

const meta = {
    title: 'Forms/Form Field',
    component: TumUiFormFieldComponent,
    decorators: [moduleMetadata({ imports: [TumUiInputDirective, TumUiSelectComponent] }), formStoryDecorator],
    args: {
        label: 'Login',
        required: false,
        hint: undefined,
        invalid: false,
        error: 'Enter a login',
    },
    render: (args) => ({
        props: args,
        template: `
            <tum-ui-form-field ${argsToTemplate(args)} style="width: min(24rem, 100%);">
                <input tumUiInput placeholder="ab12cde" />
            </tum-ui-form-field>
        `,
    }),
} satisfies Meta<FormFieldStoryArgs>;

export default meta;

type Story = StoryObj<FormFieldStoryArgs>;

export const Default: Story = {};

export const Required: Story = {
    args: { required: true },
};

export const WithHint: Story = {
    args: { hint: 'Your TUM identifier, for example ab12cde.' },
};

export const Invalid: Story = {
    args: { invalid: true, required: true },
};

/** A select adopts the field's id and description exactly as a native input does. */
export const WrappingASelect: Story = {
    args: { label: 'Language', error: 'Pick a language' },
    render: (args) => ({
        props: args,
        template: `
            <tum-ui-form-field ${argsToTemplate(args)} style="width: min(24rem, 100%);">
                <tum-ui-select [options]="['English', 'German']" placeholder="Select a language" />
            </tum-ui-form-field>
        `,
    }),
};
