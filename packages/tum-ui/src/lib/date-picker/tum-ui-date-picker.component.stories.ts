import type { Meta, StoryObj } from '@storybook/angular-vite';
import dayjs from 'dayjs/esm';
import { expect, screen, waitForElementToBeRemoved, within } from 'storybook/test';
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
    argTypes: {
        value: {
            control: false,
        },
    },
} satisfies Meta<TumUiDatePickerComponent>;

export default meta;

type Story = StoryObj<TumUiDatePickerComponent>;

export const Default: Story = {
    play: async ({ canvas, userEvent }) => {
        const trigger = canvas.getByRole('button', { name: 'Open calendar' });
        await userEvent.click(trigger);

        const dialog = await screen.findByRole('dialog', { name: 'Open calendar' });
        await expect(within(dialog).getByRole('grid')).toBeVisible();
        const selectedDay = within(dialog).getByRole('gridcell', { selected: true }).querySelector('button');
        await expect(selectedDay).toHaveFocus();

        const dialogRemoved = waitForElementToBeRemoved(dialog);
        await userEvent.keyboard('{Escape}');
        await dialogRemoved;
        await expect(trigger).toHaveFocus();
    },
};

export const Invalid: Story = {
    args: {
        error: true,
        value: undefined,
    },
};

export const Disabled: Story = {
    args: {
        disabled: true,
    },
};
