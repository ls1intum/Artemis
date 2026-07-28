import type { Meta, StoryObj } from '@storybook/angular-vite';
import dayjs from 'dayjs/esm';
import { expect, userEvent, waitFor, within } from 'storybook/test';
import { TumUiDatePickerComponent } from './tum-ui-date-picker.component';

const meta = {
    title: 'Forms/Date Picker',
    component: TumUiDatePickerComponent,
    args: {
        ariaLabel: 'Deadline',
        labelName: 'Deadline',
        shouldDisplayTimeZoneWarning: false,
        value: dayjs('2026-06-13T08:30:00'),
    },
} satisfies Meta<TumUiDatePickerComponent>;

export default meta;

type Story = StoryObj<TumUiDatePickerComponent>;

export const Default: Story = {
    play: async ({ canvas }) => {
        const trigger = canvas.getByRole('button', { name: 'Open calendar' });
        await userEvent.click(trigger);

        const dialog = await within(document.body).findByRole('dialog', { name: 'Open calendar' });
        await expect(within(dialog).getByRole('grid')).toBeVisible();
        await expect(dialog.contains(document.activeElement)).toBe(true);

        await userEvent.keyboard('{Escape}');
        await waitFor(() => expect(dialog).not.toBeInTheDocument());
    },
};

export const Invalid: Story = {
    args: {
        error: true,
        value: undefined,
    },
};

export const DarkTheme: Story = {
    ...Default,
    globals: {
        theme: 'dark',
    },
};
