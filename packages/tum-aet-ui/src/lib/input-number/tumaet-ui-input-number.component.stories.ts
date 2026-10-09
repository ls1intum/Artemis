import { argsToTemplate, moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect } from 'storybook/test';
import { formStoryDecorator } from '../../../.storybook/story-decorators';
import { TumAetUiInputGroupAddonComponent } from '../input-group/tumaet-ui-input-group-addon.component';
import { TumAetUiInputGroupComponent } from '../input-group/tumaet-ui-input-group.component';
import { TumAetUiInputNumberComponent } from './tumaet-ui-input-number.component';

const meta = {
    title: 'Forms/Input Number',
    component: TumAetUiInputNumberComponent,
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
} satisfies Meta<TumAetUiInputNumberComponent>;

export default meta;

type Story = StoryObj<TumAetUiInputNumberComponent>;

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
            imports: [TumAetUiInputGroupComponent, TumAetUiInputGroupAddonComponent],
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
            <tumaet-ui-input-group>
                <tumaet-ui-input-group-addon>#</tumaet-ui-input-group-addon>
                <tumaet-ui-input-number ${argsToTemplate(args)} />
                <tumaet-ui-input-group-addon>places</tumaet-ui-input-group-addon>
            </tumaet-ui-input-group>
            <small id="capacity-help">Maximum tutorial group size</small>
        `,
    }),
};

export const Disabled: Story = {
    args: {
        disabled: true,
    },
};
