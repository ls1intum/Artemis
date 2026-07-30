import type { Meta, StoryObj } from '@storybook/angular-vite';
import { fn } from 'storybook/test';

import { TumUiChipComponent } from './tum-ui-chip.component';

const meta = {
    title: 'Data Display/Chip',
    component: TumUiChipComponent,
    args: {
        label: 'Machine Learning',
        removed: fn(),
        removable: false,
        removeAriaLabel: 'Remove Machine Learning',
    },
} satisfies Meta<TumUiChipComponent>;

export default meta;

type Story = StoryObj<TumUiChipComponent>;

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
