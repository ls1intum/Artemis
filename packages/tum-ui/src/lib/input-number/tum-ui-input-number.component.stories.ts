import { argsToTemplate, moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect } from 'storybook/test';
import { formStoryDecorator } from '../../../.storybook/story-decorators';
import { TumUiInputGroupAddonComponent } from '../input-group/tum-ui-input-group-addon.component';
import { TumUiInputGroupComponent } from '../input-group/tum-ui-input-group.component';
import { TumUiInputNumberComponent } from './tum-ui-input-number.component';

const meta = {
    title: 'Forms/Input Number',
    component: TumUiInputNumberComponent,
    args: {
        ariaLabel: 'Capacity',
        fluid: true,
        locale: 'en-US',
        max: 5000,
        min: 1,
        placeholder: 'Capacity',
        showButtons: true,
        step: 1,
        useGrouping: true,
    },
    decorators: [formStoryDecorator],
} satisfies Meta<TumUiInputNumberComponent>;

export default meta;

type Story = StoryObj<TumUiInputNumberComponent>;

export const Default: Story = {};

export const FormatsTypedValue: Story = {
    tags: ['!dev', '!autodocs'],
    play: async ({ canvas, userEvent }) => {
        const input = canvas.getByRole('spinbutton', { name: 'Capacity' });
        await userEvent.type(input, '1234');
        await expect(input).toHaveValue('1,234');
    },
};

export const WithAffixes: Story = {
    args: {
        ariaLabel: 'Repetition frequency',
        max: 48,
        placeholder: undefined,
        prefix: 'every ',
        suffix: ' week(s)',
    },
};

export const InInputGroup: Story = {
    decorators: [
        moduleMetadata({
            imports: [TumUiInputGroupComponent, TumUiInputGroupAddonComponent],
        }),
    ],
    args: {
        ariaDescribedBy: 'capacity-help',
        ariaLabel: undefined,
        inputId: 'grouped-capacity',
        min: 0,
    },
    render: (args) => ({
        props: args,
        template: `
            <label for="grouped-capacity">Capacity</label>
            <tum-ui-input-group>
                <tum-ui-input-group-addon>#</tum-ui-input-group-addon>
                <tum-ui-input-number ${argsToTemplate(args)} />
                <tum-ui-input-group-addon>places</tum-ui-input-group-addon>
            </tum-ui-input-group>
            <small id="capacity-help">Maximum tutorial group size</small>
        `,
    }),
};

export const Disabled: Story = {
    args: {
        disabled: true,
    },
};
