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
        rounded: {
            control: 'boolean',
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

/** Icon-only native buttons and links can use the same fully rounded presentation as `tumaet-ui-button [rounded]`. */
export const RoundedIconOnly: Story = {
    args: {
        severity: 'secondary',
        size: 'small',
        variant: 'text',
        rounded: true,
    },
    render: (args) => ({
        props: args,
        template: `
            <button type="button" tumAetUiButton ${argsToTemplate(args)} aria-label="Open session actions">⋮</button>
            <a href="#rounded-link" tumAetUiButton ${argsToTemplate(args)} aria-label="Open exercise">→</a>
        `,
    }),
    play: async ({ canvas, userEvent }) => {
        const button = canvas.getByRole('button', { name: 'Open session actions' });
        const link = canvas.getByRole('link', { name: 'Open exercise' });
        await expect(getComputedStyle(button).borderRadius).toBe('9999px');
        await expect(getComputedStyle(link).borderRadius).toBe('9999px');
        await userEvent.tab();
        await expect(button).toHaveFocus();
        await expect(getComputedStyle(button).outlineStyle).not.toBe('none');
        await userEvent.tab();
        await expect(link).toHaveFocus();
    },
};
