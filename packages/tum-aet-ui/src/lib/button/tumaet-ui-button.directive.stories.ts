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
