import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect, fn } from 'storybook/test';

import { TumUiChipComponent } from './tum-ui-chip.component';

const meta = {
    title: 'Data Display/Chip',
    component: TumUiChipComponent,
    args: {
        label: 'Machine Learning',
        onRemove: fn(),
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
    play: async ({ args, canvas, userEvent }) => {
        await userEvent.click(canvas.getByRole('button', { name: 'Remove Machine Learning' }));
        await expect(args.onRemove).toHaveBeenCalledOnce();
    },
};

export const Compact: Story = {
    args: {
        label: 'Compact',
        size: 'small',
    },
};
