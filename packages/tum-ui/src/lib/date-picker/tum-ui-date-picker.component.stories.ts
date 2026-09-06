import type { Meta, StoryObj } from '@storybook/angular-vite';
import dayjs from 'dayjs/esm';
import { expect, screen, waitForElementToBeRemoved, within } from 'storybook/test';
import { formStoryDecorator } from '../../../.storybook/story-decorators';
import { TumUiDatePickerComponent } from './tum-ui-date-picker.component';

const meta = {
    title: 'Forms/Date Picker',
    component: TumUiDatePickerComponent,
    args: {
        ariaLabel: 'Deadline',
        labelName: 'Deadline',
        value: dayjs('2026-06-13T08:30:00'),
    },
    argTypes: {
        value: {
            control: false,
        },
    },
    decorators: [formStoryDecorator],
} satisfies Meta<TumUiDatePickerComponent>;

export default meta;

type Story = StoryObj<TumUiDatePickerComponent>;

export const Default: Story = {};

export const CalendarInteraction: Story = {
    tags: ['!dev', '!autodocs'],
    play: async ({ canvas, userEvent }) => {
        const trigger = canvas.getByRole('button', { name: 'Open calendar' });
        await userEvent.click(trigger);

        const dialog = await screen.findByRole('dialog', { name: 'Choose date and time' });
        await expect(within(dialog).getByRole('grid')).toBeVisible();
        const selectedDay = within(dialog).getByRole('gridcell', { selected: true }).querySelector('button');
        await expect(selectedDay).toHaveFocus();

        const dialogRemoved = waitForElementToBeRemoved(dialog);
        await userEvent.keyboard('{Escape}');
        await dialogRemoved;
        await expect(trigger).toHaveFocus();

        await userEvent.click(canvas.getByRole('button', { name: 'Clear date' }));
        const input = canvas.getByRole('combobox', { name: 'Deadline' });
        await expect(input).toHaveFocus();
        await expect(getComputedStyle(input).outlineWidth).toBe('2px');
    },
};

export const TimeOnly: Story = {
    args: {
        ariaLabel: 'Session start',
        labelName: 'Session start',
        timeOnly: true,
    },
    play: async ({ canvas, userEvent }) => {
        const field = canvas.getByRole('combobox', { name: 'Session start' });
        await expect(field).toHaveValue('08:30');

        const trigger = canvas.getByRole('button', { name: 'Open clock' });
        await userEvent.click(trigger);

        // The dialog is the clock alone: there is no calendar to page through.
        const dialog = await screen.findByRole('dialog', { name: 'Choose time' });
        await expect(within(dialog).queryByRole('grid')).toBeNull();

        const hour = within(dialog).getByRole('textbox', { name: 'Hour' });
        await userEvent.clear(hour);
        await userEvent.type(hour, '10');
        await userEvent.tab();

        await expect(field).toHaveValue('10:30');
    },
};

export const TimeOnlyInvalid: Story = {
    args: {
        ariaLabel: 'Session start',
        labelName: 'Session start',
        timeOnly: true,
    },
    tags: ['!autodocs'],
    play: async ({ canvas, userEvent }) => {
        const field = canvas.getByRole('combobox', { name: 'Session start' });
        await userEvent.clear(field);
        await userEvent.type(field, '13.06.2026 08:30');

        // A full date is not what this field accepts, and the message says so in its own terms.
        await expect(canvas.getByRole('alert')).toHaveTextContent('Enter a valid time.');
    },
};

export const Invalid: Story = {
    args: {
        invalid: true,
        value: undefined,
    },
};

export const Disabled: Story = {
    args: {
        disabled: true,
    },
};
