import { faCircleInfo } from '@fortawesome/free-solid-svg-icons';
import type { Meta, StoryObj } from '@storybook/angular-vite';

import { TumAetUiMessageComponent, TumAetUiMessageSeverity } from './tumaet-ui-message.component';

const severities: TumAetUiMessageSeverity[] = ['info', 'success', 'warn', 'error', 'secondary', 'contrast'];
const meta = {
    title: 'Feedback/Message',
    component: TumAetUiMessageComponent,
    args: {
        text: 'The exercise opens tomorrow.',
        severity: 'info',
        icon: faCircleInfo,
    },
    argTypes: {
        icon: {
            control: false,
        },
        severity: {
            control: 'select',
            options: severities,
        },
    },
} satisfies Meta<TumAetUiMessageComponent>;

export default meta;

type Story = StoryObj<TumAetUiMessageComponent>;

export const Default: Story = {};

export const Success: Story = {
    args: {
        icon: undefined,
        severity: 'success',
        text: 'Your changes have been saved.',
    },
};

export const Warning: Story = {
    args: {
        icon: undefined,
        severity: 'warn',
        text: 'The submission deadline is approaching.',
    },
};

export const Error: Story = {
    args: {
        icon: undefined,
        severity: 'error',
        text: 'The submission could not be uploaded.',
    },
};

export const Secondary: Story = {
    args: {
        icon: undefined,
        severity: 'secondary',
        text: 'No assessment is available yet.',
    },
};

export const Contrast: Story = {
    args: {
        icon: undefined,
        severity: 'contrast',
        text: 'Maintenance starts at 22:00.',
    },
};
