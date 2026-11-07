import type { Meta, StoryObj } from '@storybook/angular-vite';
import { fn } from 'storybook/test';

import { TumAetUiChipComponent } from './tumaet-ui-chip.component';

const meta = {
    title: 'Data Display/Chip',
    component: TumAetUiChipComponent,
    args: {
        label: 'Machine Learning',
        removed: fn(),
        removable: false,
        removeAriaLabel: 'Remove Machine Learning',
    },
} satisfies Meta<TumAetUiChipComponent>;

export default meta;

type Story = StoryObj<TumAetUiChipComponent>;

export const Default: Story = {};

export const Removable: Story = {
    args: {
        removable: true,
    },
};

export const Compact: Story = {
    args: {
        label: 'Compact',
        size: 'small',
    },
};

/** A label longer than the space its container leaves truncates with an ellipsis; the remove button stays visible. */
export const Truncated: Story = {
    args: {
        label: 'Introduction to Machine Learning and Neural Networks',
        removable: true,
    },
    render: (args) => ({
        props: args,
        template: `<div style="width: 12rem"><tumaet-ui-chip [label]="label" [removable]="removable" [removeAriaLabel]="removeAriaLabel" (removed)="removed($event)" /></div>`,
    }),
};
