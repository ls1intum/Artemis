import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect, fn, userEvent, waitFor, within } from 'storybook/test';
import { TumUiAutoCompleteComponent } from './tum-ui-autocomplete.component';

const meta = {
    title: 'Forms/Autocomplete',
    component: TumUiAutoCompleteComponent,
    args: {
        ariaLabel: 'Assignee',
        completeMethod: fn(),
        completeOnFocus: true,
        delay: 0,
        emptyMessage: 'No matching people',
        placeholder: 'Search people',
        suggestions: ['Ada Lovelace', 'Grace Hopper', 'Margaret Hamilton'],
    },
    parameters: {
        layout: 'centered',
    },
} satisfies Meta<TumUiAutoCompleteComponent>;

export default meta;

type Story = StoryObj<TumUiAutoCompleteComponent>;

export const Default: Story = {
    play: async ({ canvas }) => {
        const input = canvas.getByRole('combobox', { name: 'Assignee' });
        await userEvent.click(input);

        const listbox = await within(document.body).findByRole('listbox', { name: 'Assignee' });
        await userEvent.keyboard('{ArrowDown}{Enter}');

        await expect(input).toHaveValue('Ada Lovelace');
        await waitFor(() => expect(listbox).not.toBeInTheDocument());
    },
};

export const NoResults: Story = {
    args: {
        suggestions: [],
    },
};
