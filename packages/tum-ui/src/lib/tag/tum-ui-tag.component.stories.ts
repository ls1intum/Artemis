import type { Meta, StoryObj } from '@storybook/angular-vite';

import { TumUiTagComponent, TumUiTagSeverity } from './tum-ui-tag.component';

const severities: TumUiTagSeverity[] = ['secondary', 'success', 'info', 'warn', 'danger', 'contrast'];
const meta = {
    title: 'Data Display/Tag',
    component: TumUiTagComponent,
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
} satisfies Meta<TumUiTagComponent>;

export default meta;

type Story = StoryObj<TumUiTagComponent>;

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
