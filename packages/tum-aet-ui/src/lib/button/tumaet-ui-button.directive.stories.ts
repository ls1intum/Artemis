import { expect } from 'storybook/test';
import { tumAetUiButtonClasses } from './tumaet-ui-button.variants';
import { argsToTemplate } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';

import { TumAetUiButtonDirective } from './tumaet-ui-button.directive';

const meta = {
    title: 'Actions/Button Directive',
    component: TumAetUiButtonDirective,
    args: {
        severity: 'primary',
        size: 'default',
        variant: 'solid',
    },
    argTypes: {
        disabled: { control: 'boolean' },
        severity: {
            control: 'select',
            options: ['primary', 'secondary', 'success', 'info', 'warn', 'danger', 'contrast'],
        },
        size: {
            control: 'inline-radio',
            options: ['small', 'default', 'large'],
        },
        variant: {
            control: 'inline-radio',
            options: ['solid', 'outlined', 'text'],
        },
    },
    render: (args) => ({
        props: args,
        template: `<button tumAetUiButton ${argsToTemplate(args)}>Native button</button>`,
    }),
} satisfies Meta<TumAetUiButtonDirective>;

export default meta;

type Story = StoryObj<TumAetUiButtonDirective>;

export const Default: Story = {};

export const Link: Story = {
    args: {
        variant: 'text',
    },
    render: (args) => ({
        props: args,
        template: `<a href="#button-directive" tumAetUiButton ${argsToTemplate(args)}>Course details</a>`,
    }),
};

export const DisabledLink: Story = {
    args: { disabled: true },
    render: (args) => ({
        props: args,
        template: `<a href="#button-directive" tumAetUiButton ${argsToTemplate(args)}>Unavailable action</a>`,
    }),
};

/** Native hosts using the public variant helper retain the same keyboard focus treatment. */
export const NativeVariantHelper: Story = {
    render: () => ({
        props: { classes: tumAetUiButtonClasses({ severity: 'danger', size: 'small', variant: 'solid' }) },
        template: `<button [class]="classes">Delete selection</button>`,
    }),
    play: async ({ canvas, userEvent }) => {
        const button = canvas.getByRole('button', { name: 'Delete selection' });
        await userEvent.tab();
        await expect(button).toHaveFocus();
        const style = getComputedStyle(button);
        await expect(style.outlineStyle).not.toBe('none');
        await expect(parseFloat(style.outlineWidth)).toBeGreaterThanOrEqual(2);
    },
};
