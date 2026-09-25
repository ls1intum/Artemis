import { argsToTemplate } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { faDownload } from '@fortawesome/free-solid-svg-icons';
import type { IconProp } from '@fortawesome/fontawesome-svg-core';
import { fn } from 'storybook/test';
import { TumAetUiButtonComponent } from './tumaet-ui-button.component';
import { TumAetUiButtonSeverity, TumAetUiButtonSize, TumAetUiButtonVariant } from './tumaet-ui-button.variants';

const severities: TumAetUiButtonSeverity[] = ['primary', 'secondary', 'success', 'info', 'warn', 'danger', 'contrast'];
const variants: TumAetUiButtonVariant[] = ['solid', 'outlined', 'text'];

interface ButtonStoryArgs {
    label: string;
    severity: TumAetUiButtonSeverity;
    size: TumAetUiButtonSize;
    variant: TumAetUiButtonVariant;
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
    component: TumAetUiButtonComponent,
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
            <tumaet-ui-button ${argsToTemplate(args)}>
                {{ label }}
            </tumaet-ui-button>
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
};

export const Loading: Story = {
    args: {
        loading: true,
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
