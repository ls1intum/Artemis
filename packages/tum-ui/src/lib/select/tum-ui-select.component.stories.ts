import { FormsModule } from '@angular/forms';
import { moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect, fireEvent, fn, screen, waitForElementToBeRemoved, within } from 'storybook/test';
import { formStoryDecorator } from '../../../.storybook/story-decorators';
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
    decorators: [
        formStoryDecorator,
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

export const Filterable: Story = {
    args: {
        filter: true,
        options: [
            { code: 'en', label: 'English' },
            { code: 'de', label: 'German' },
            { code: 'es', label: 'Spanish' },
            { code: 'fr', label: 'French' },
            { code: 'it', label: 'Italian' },
            { code: 'pt', label: 'Portuguese' },
        ],
        showClear: false,
    },
    play: async ({ canvas, userEvent, args }) => {
        const trigger = canvas.getByRole('combobox', { name: 'Course language' });
        await userEvent.click(trigger);

        // Focus moves to the search field, so what the user types filters instead of running the typeahead.
        const search = await screen.findByRole('textbox', { name: 'Filter options' });
        await expect(search).toHaveFocus();

        await userEvent.type(search, 'ish');
        const listbox = await screen.findByRole('listbox', { name: 'Course language' });
        await expect(within(listbox).getAllByRole('option')).toHaveLength(2);

        await userEvent.click(within(listbox).getByText('Spanish'));
        await expect(trigger).toHaveTextContent('Spanish');
        await expect(args.selectionChange).toHaveBeenCalledWith('es');
    },
};

export const FilterWithoutMatches: Story = {
    args: {
        filter: true,
    },
    tags: ['!autodocs'],
    play: async ({ canvas, userEvent }) => {
        await userEvent.click(canvas.getByRole('combobox', { name: 'Course language' }));
        await userEvent.type(await screen.findByRole('textbox', { name: 'Filter options' }), 'zzz');

        await expect(await screen.findByText('No matching options')).toBeVisible();
    },
};

export const Disabled: Story = {
    args: {
        disabled: true,
    },
};
