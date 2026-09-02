import { argsToTemplate } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';

import { TumUiButtonDirective } from './tum-ui-button.directive';

const meta = {
    title: 'Actions/Button Directive',
    component: TumUiButtonDirective,
    args: {
        severity: 'primary',
        size: 'medium',
        variant: 'solid',
    },
    argTypes: {
        severity: {
            control: 'select',
            options: ['primary', 'secondary', 'success', 'info', 'warning', 'danger', 'contrast'],
        },
        size: {
            control: 'inline-radio',
            options: ['small', 'medium', 'large'],
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
