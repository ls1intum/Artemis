import { expect } from 'storybook/test';
import { argsToTemplate } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';

import { TumUiButtonDirective } from './tum-ui-button.directive';

const meta = {
    title: 'Actions/Button Directive',
    component: TumUiButtonDirective,
    args: {
        severity: 'primary',
        size: 'default',
        variant: 'solid',
        rounded: false,
    },
    argTypes: {
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
        template: `<button tumUiButton ${argsToTemplate(args)}>Native button</button>`,
    }),
} satisfies Meta<TumUiButtonDirective>;

export default meta;

type Story = StoryObj<TumUiButtonDirective>;

export const Default: Story = {};

export const Link: Story = {
    args: {
        variant: 'text',
    },
    render: (args) => ({
        props: args,
        template: `<a href="#button-directive" tumUiButton ${argsToTemplate(args)}>Course details</a>`,
    }),
};

export const Rounded: Story = {
    args: { rounded: true },
    play: async ({ canvas, userEvent }) => {
        const button = canvas.getByRole('button', { name: 'Native button' });
        await userEvent.tab();
        await expect(button).toHaveFocus();
        await expect(getComputedStyle(button).borderRadius).toBe('9999px');
    },
};

export const AriaDisabled: Story = {
    render: (args) => ({
        props: args,
        template: `<button tumUiButton ${argsToTemplate(args)} aria-disabled="true">Unavailable action</button>`,
    }),
    play: async ({ canvas, userEvent }) => {
        const button = canvas.getByRole('button', { name: 'Unavailable action' });
        await userEvent.tab();
        await expect(button).toHaveFocus();
        await expect(getComputedStyle(button).opacity).toBe('0.6');
        await expect(getComputedStyle(button).cursor).toBe('not-allowed');
    },
};
