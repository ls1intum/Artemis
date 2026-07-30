import { faCircleInfo } from '@fortawesome/free-solid-svg-icons';
import type { Meta, StoryObj } from '@storybook/angular-vite';

import { TumUiMessageComponent, TumUiMessageSeverity } from './tum-ui-message.component';

const severities: TumUiMessageSeverity[] = ['info', 'success', 'warn', 'error', 'secondary', 'contrast'];
const meta = {
    title: 'Feedback/Message',
    component: TumUiMessageComponent,
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
} satisfies Meta<TumUiMessageComponent>;

export default meta;

type Story = StoryObj<TumUiMessageComponent>;

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
