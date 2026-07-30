import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect, fn, screen, waitForElementToBeRemoved } from 'storybook/test';
import { TumUiSelectComponent } from './tum-ui-select.component';

const languages = [
    { code: 'en', label: 'English' },
    { code: 'de', label: 'German' },
    { code: 'es', label: 'Spanish' },
];

const meta = {
    title: 'Forms/Select',
    component: TumUiSelectComponent,
    args: {
        ariaLabel: 'Course language',
        onChange: fn(),
        optionLabel: 'label',
        optionValue: 'code',
        options: languages,
        placeholder: 'Choose a language',
        showClear: true,
    },
    argTypes: {
        size: {
            control: 'inline-radio',
            options: [undefined, 'small', 'large'],
        },
    },
    parameters: {
        layout: 'centered',
    },
} satisfies Meta<TumUiSelectComponent>;

export default meta;

type Story = StoryObj<TumUiSelectComponent>;

export const Default: Story = {};

export const SelectsOption: Story = {
    tags: ['!dev', '!autodocs'],
    play: async ({ args, canvas, userEvent }) => {
        const trigger = canvas.getByRole('button', { name: 'Course language' });
        await userEvent.click(trigger);

        const listbox = await screen.findByRole('listbox', { name: 'Course language' });
        await expect(listbox).toHaveFocus();
        const listboxRemoved = waitForElementToBeRemoved(listbox);
        await userEvent.keyboard('{End}{Enter}');

        await expect(trigger).toHaveTextContent('Spanish');
        await expect(args.onChange).toHaveBeenCalledWith('es');
        await listboxRemoved;
        await expect(trigger).toHaveFocus();
    },
};

export const Empty: Story = {
    args: {
        emptyMessage: 'No languages available',
        options: [],
    },
    play: async ({ canvas, userEvent }) => {
        await userEvent.click(canvas.getByRole('button', { name: 'Course language' }));
        await expect(await screen.findByText('No languages available')).toBeVisible();
    },
};

export const Disabled: Story = {
    args: {
        disabled: true,
    },
};
