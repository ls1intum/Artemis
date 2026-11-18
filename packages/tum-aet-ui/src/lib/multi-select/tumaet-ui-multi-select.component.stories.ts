import { FormsModule } from '@angular/forms';
import { moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect, fn, screen } from 'storybook/test';
import { formStoryDecorator } from '../../../.storybook/story-decorators';
import { TumAetUiMultiSelectComponent } from './tumaet-ui-multi-select.component';

const eventTypes = [
    { code: 'lectures', label: 'Lectures' },
    { code: 'exercises', label: 'Exercises' },
    { code: 'tutorials', label: 'Tutorials' },
    { code: 'exams', label: 'Exams' },
];

const meta = {
    title: 'Forms/Multi Select',
    component: TumAetUiMultiSelectComponent,
    args: {
        ariaLabel: 'Event types',
        selectionChange: fn(),
        optionLabel: 'label',
        optionValue: 'code',
        options: eventTypes,
        placeholder: 'Choose event types',
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
} satisfies Meta<TumAetUiMultiSelectComponent>;

export default meta;

type Story = StoryObj<TumAetUiMultiSelectComponent>;

export const Default: Story = {};

export const ChoosesSeveralOptions: Story = {
    parameters: {
        docs: {
            story: { autoplay: true },
        },
    },
    play: async ({ args, canvas, userEvent }) => {
        const trigger = canvas.getByRole('combobox', { name: 'Event types' });
        await userEvent.click(trigger);
        await userEvent.click(await screen.findByRole('option', { name: 'Lectures' }));
        await userEvent.click(await screen.findByRole('option', { name: 'Exams' }));

        await expect(trigger).toHaveTextContent('Lectures, Exams');
        await expect(args.selectionChange).toHaveBeenLastCalledWith(['lectures', 'exams']);
        await expect(await screen.findByRole('listbox', { name: 'Event types' })).toHaveAttribute('aria-multiselectable', 'true');
    },
};

export const CountsBeyondTheLabelLimit: Story = {
    args: { maxSelectedLabels: 1 },
    play: async ({ canvas, userEvent }) => {
        const trigger = canvas.getByRole('combobox', { name: 'Event types' });
        await userEvent.click(trigger);
        await userEvent.click(await screen.findByRole('option', { name: 'Lectures' }));
        await userEvent.click(await screen.findByRole('option', { name: 'Exercises' }));

        await expect(trigger).toHaveTextContent('2 selected');
    },
};

export const Outlined: Story = {
    args: { variant: 'outlined', size: 'small' },
};
