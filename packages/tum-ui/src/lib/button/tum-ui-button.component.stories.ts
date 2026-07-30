import { argsToTemplate } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { faDownload } from '@fortawesome/free-solid-svg-icons';
import type { IconProp } from '@fortawesome/fontawesome-svg-core';
import { expect, fn } from 'storybook/test';
import { TumUiButtonComponent } from './tum-ui-button.component';
import { TumUiButtonSeverity, TumUiButtonSize, TumUiButtonVariant } from './tum-ui-button.variants';

const severities: TumUiButtonSeverity[] = ['primary', 'secondary', 'success', 'info', 'warn', 'danger', 'contrast'];
const variants: TumUiButtonVariant[] = ['solid', 'outlined', 'text'];

interface ButtonStoryArgs {
    label: string;
    severity: TumUiButtonSeverity;
    size: TumUiButtonSize;
    variant: TumUiButtonVariant;
    disabled: boolean;
    rounded: boolean;
    loading: boolean;
    icon: IconProp | undefined;
    ariaLabel: string | undefined;
    clicked: (event: MouseEvent) => void;
}

const meta = {
    id: 'actions-button',
    title: 'Actions/Button',
    component: TumUiButtonComponent,
    args: {
        clicked: fn(),
        label: 'Continue',
        severity: 'primary',
        size: 'default',
        variant: 'solid',
        disabled: false,
        rounded: false,
        loading: false,
        icon: undefined,
        ariaLabel: undefined,
    },
    argTypes: {
        severity: {
            control: 'select',
            options: severities,
        },
        size: {
            control: 'select',
            options: ['small', 'default', 'large'],
        },
        variant: {
            control: 'select',
            options: variants,
        },
        icon: {
            control: false,
        },
    },
    render: ({ label, ...args }) => ({
        props: { ...args, label },
        template: `
            <tum-ui-button ${argsToTemplate(args)}>
                {{ label }}
            </tum-ui-button>
        `,
    }),
    parameters: {
        layout: 'centered',
    },
} satisfies Meta<ButtonStoryArgs>;

export default meta;

type Story = StoryObj<ButtonStoryArgs>;

export const Default: Story = {};

export const Outlined: Story = {
    args: {
        variant: 'outlined',
    },
};

export const Text: Story = {
    args: {
        variant: 'text',
    },
};

export const Disabled: Story = {
    args: {
        disabled: true,
    },
    play: async ({ canvas }) => {
        await expect(canvas.getByRole('button', { name: 'Continue' })).toBeDisabled();
    },
};

export const Loading: Story = {
    args: {
        loading: true,
    },
    play: async ({ canvas }) => {
        const button = canvas.getByRole('button', { name: 'Continue' });
        await expect(button).toBeDisabled();
        await expect(button).toHaveAttribute('aria-busy', 'true');
    },
};

export const IconOnly: Story = {
    args: {
        ariaLabel: 'Download results',
        icon: faDownload,
        label: '',
        rounded: true,
    },
};
