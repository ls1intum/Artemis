import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect, fn, userEvent, waitFor, within } from 'storybook/test';
import { TumUiSelectComponent } from './tum-ui-select.component';

const meta = {
    title: 'Forms/Select',
    component: TumUiSelectComponent,
    args: {
        ariaLabel: 'Course language',
        onChange: fn(),
        options: ['English', 'German', 'Spanish'],
        placeholder: 'Choose a language',
        showClear: true,
    },
    parameters: {
        layout: 'centered',
    },
} satisfies Meta<TumUiSelectComponent>;

export default meta;

type Story = StoryObj<TumUiSelectComponent>;

export const Default: Story = {
    play: async ({ args, canvas }) => {
        const trigger = canvas.getByRole('button', { name: 'Course language' });
        await userEvent.click(trigger);

        const listbox = await within(document.body).findByRole('listbox', { name: 'Course language' });
        await expect(listbox).toHaveFocus();
        await userEvent.keyboard('{End}{Enter}');

        await expect(trigger).toHaveTextContent('Spanish');
        await expect(args.onChange).toHaveBeenCalledWith('Spanish');
        await waitFor(() => expect(listbox).not.toBeInTheDocument());
    },
};

export const Empty: Story = {
    args: {
        emptyMessage: 'No languages available',
        options: [],
    },
};
