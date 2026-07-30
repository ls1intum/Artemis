import { FormsModule } from '@angular/forms';
import { moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect, fireEvent, fn, screen, waitForElementToBeRemoved } from 'storybook/test';
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
        selectionChange: fn(),
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
    decorators: [
        moduleMetadata({
            imports: [FormsModule],
        }),
    ],
} satisfies Meta<TumUiSelectComponent>;

export default meta;

type Story = StoryObj<TumUiSelectComponent>;

export const Default: Story = {};

export const Selected: Story = {
    parameters: {
        docs: {
            story: { autoplay: true },
        },
    },
    play: async ({ canvas, userEvent }) => {
        const trigger = canvas.getByRole('combobox', { name: 'Course language' });
        await userEvent.click(trigger);
        await userEvent.click(await screen.findByRole('option', { name: 'German' }));
        await expect(trigger).toHaveTextContent('German');
    },
};

export const SelectsOption: Story = {
    tags: ['!dev', '!autodocs'],
    play: async ({ args, canvas, userEvent }) => {
        const trigger = canvas.getByRole('combobox', { name: 'Course language' });
        await userEvent.click(trigger);

        const listbox = await screen.findByRole('listbox', { name: 'Course language' });
        await expect(trigger).toHaveFocus();
        const listboxRemoved = waitForElementToBeRemoved(listbox);
        await fireEvent.keyDown(trigger, { key: 'End', keyCode: 35 });
        await userEvent.keyboard('{Enter}');

        await expect(trigger).toHaveTextContent('Spanish');
        await expect(args.selectionChange).toHaveBeenCalledWith('es');
        await listboxRemoved;
        await expect(trigger).toHaveFocus();

        await userEvent.tab();
        const clear = canvas.getByRole('button', { name: 'Clear selection' });
        await expect(clear).toHaveFocus();
        await userEvent.keyboard('{Enter}');
        await expect(trigger).toHaveFocus();
        await expect(trigger).toHaveTextContent('Choose a language');
    },
};

export const Empty: Story = {
    args: {
        emptyMessage: 'No languages available',
        options: [],
    },
    tags: ['!autodocs'],
    play: async ({ canvas, userEvent }) => {
        await userEvent.click(canvas.getByRole('combobox', { name: 'Course language' }));
        await expect(await screen.findByText('No languages available')).toBeVisible();
    },
};

export const Disabled: Story = {
    args: {
        disabled: true,
    },
};
