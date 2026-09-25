import type { Meta, StoryObj } from '@storybook/angular-vite';

import { TumAetUiTagComponent, TumAetUiTagSeverity } from './tumaet-ui-tag.component';

const severities: TumAetUiTagSeverity[] = ['secondary', 'success', 'info', 'warn', 'danger', 'contrast'];
const meta = {
    title: 'Data Display/Tag',
    component: TumAetUiTagComponent,
    args: {
        value: 'Draft',
        severity: 'secondary',
        rounded: false,
    },
    argTypes: {
        severity: {
            control: 'select',
            options: severities,
        },
    },
} satisfies Meta<TumAetUiTagComponent>;

export default meta;

type Story = StoryObj<TumAetUiTagComponent>;

export const Default: Story = {};

export const Published: Story = {
    args: {
        severity: 'success',
        value: 'Published',
    },
};

export const InReview: Story = {
    args: {
        severity: 'info',
        value: 'In review',
    },
};

export const DueSoon: Story = {
    args: {
        severity: 'warn',
        value: 'Due soon',
    },
};

export const Overdue: Story = {
    args: {
        severity: 'danger',
        value: 'Overdue',
    },
};

export const Archived: Story = {
    args: {
        severity: 'contrast',
        value: 'Archived',
    },
};
