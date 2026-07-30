import { FormsModule } from '@angular/forms';
import { argsToTemplate, moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect, fn, screen, waitForElementToBeRemoved } from 'storybook/test';
import { TumUiAutoCompleteComponent } from './tum-ui-autocomplete.component';

const meta = {
    title: 'Forms/Autocomplete',
    component: TumUiAutoCompleteComponent,
    args: {
        ariaLabel: 'Assignee',
        searchRequested: fn(),
        completeOnFocus: true,
        debounceMs: 0,
        emptyMessage: 'No matching people',
        optionSelected: fn(),
        optionRemoved: fn(),
        placeholder: 'Search people',
        suggestions: ['Ada Lovelace', 'Grace Hopper', 'Margaret Hamilton'],
    },
    decorators: [
        moduleMetadata({
            imports: [FormsModule],
        }),
    ],
    parameters: {
        layout: 'centered',
    },
} satisfies Meta<TumUiAutoCompleteComponent>;

export default meta;

type Story = StoryObj<TumUiAutoCompleteComponent>;

export const Default: Story = {};

export const SelectsSuggestion: Story = {
    tags: ['!dev', '!autodocs'],
    play: async ({ args, canvas, userEvent }) => {
        const input = canvas.getByRole('combobox', { name: 'Assignee' });
        await userEvent.click(input);
        await expect(args.searchRequested).toHaveBeenCalledWith({ originalEvent: expect.any(FocusEvent), query: '' });

        const listbox = await screen.findByRole('listbox', { name: 'Assignee' });
        const listboxRemoved = waitForElementToBeRemoved(listbox);
        await userEvent.keyboard('{ArrowDown}{Enter}');

        await expect(input).toHaveValue('Ada Lovelace');
        await expect(args.optionSelected).toHaveBeenCalledWith({ originalEvent: expect.any(KeyboardEvent), value: 'Ada Lovelace' });
        await listboxRemoved;
    },
};

export const NoResults: Story = {
    args: {
        suggestions: [],
    },
    tags: ['!autodocs'],
    play: async ({ canvas, userEvent }) => {
        await userEvent.click(canvas.getByRole('combobox', { name: 'Assignee' }));
        await expect(await screen.findByText('No matching people')).toBeVisible();
    },
};

export const Multiple: Story = {
    args: {
        multiple: true,
    },
    render: (args) => ({
        props: {
            ...args,
            selected: ['Ada Lovelace'],
        },
        template: `
            <tum-ui-autocomplete
                ${argsToTemplate(args, { exclude: ['multiple'] })}
                [multiple]="true"
                [ngModel]="selected"
            />
        `,
    }),
};

export const Disabled: Story = {
    args: {
        disabled: true,
    },
};
