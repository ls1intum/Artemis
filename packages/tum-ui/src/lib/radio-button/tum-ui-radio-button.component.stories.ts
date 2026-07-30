import { FormsModule } from '@angular/forms';
import { moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { useArgs } from 'storybook/preview-api';
import { expect, fn, waitFor } from 'storybook/test';

import { TumUiRadioButtonComponent, TumUiRadioButtonSelectEvent } from './tum-ui-radio-button.component';

interface RadioOption {
    label: string;
    value: string;
}

interface RadioButtonStoryArgs {
    legend: string;
    options: readonly RadioOption[];
    selectedValue: string | undefined;
    disabled: boolean;
    selected: (event: TumUiRadioButtonSelectEvent) => void;
}

const OPTIONS: readonly RadioOption[] = [
    { label: 'Daily', value: 'daily' },
    { label: 'Weekly', value: 'weekly' },
    { label: 'Never', value: 'never' },
];

const meta = {
    title: 'Forms/Radio Button',
    component: TumUiRadioButtonComponent,
    decorators: [
        moduleMetadata({
            imports: [FormsModule],
        }),
    ],
    args: {
        legend: 'Email summary',
        options: OPTIONS,
        selectedValue: 'weekly',
        disabled: false,
        selected: fn(),
    },
    argTypes: {
        options: {
            control: false,
        },
        selectedValue: {
            control: 'inline-radio',
            options: OPTIONS.map((option) => option.value),
        },
    },
    render: function Render(args) {
        const [{ selectedValue }, updateArgs] = useArgs<RadioButtonStoryArgs>();
        return {
            props: {
                ...args,
                selectedValue,
                selectValue(this: RadioButtonStoryArgs, value: string) {
                    this.selectedValue = value;
                    updateArgs({ selectedValue: value });
                },
            },
            template: `
                <fieldset>
                    <legend>{{ legend }}</legend>
                    @for (option of options; track option.value) {
                        <label [for]="'summary-' + option.value">
                            <tum-ui-radio-button
                                [inputId]="'summary-' + option.value"
                                name="summary"
                                [value]="option.value"
                                [ngModel]="selectedValue"
                                (ngModelChange)="selectValue($event)"
                                [ngModelOptions]="{ standalone: true }"
                                [disabled]="disabled"
                                (selected)="selected($event)"
                            />
                            {{ option.label }}
                        </label>
                    }
                </fieldset>
            `,
        };
    },
} satisfies Meta<RadioButtonStoryArgs>;

export default meta;

type Story = StoryObj<RadioButtonStoryArgs>;

export const Default: Story = {};

export const Selection: Story = {
    tags: ['!dev', '!autodocs'],
    play: async ({ args, canvas, userEvent }) => {
        await expect(canvas.getByRole('radio', { name: 'Weekly' })).toBeChecked();
        await userEvent.click(canvas.getByRole('radio', { name: 'Daily' }));
        await waitFor(() => expect(canvas.getByRole('radio', { name: 'Daily' })).toBeChecked());
        await waitFor(() => expect(canvas.getByRole('radio', { name: 'Weekly' })).not.toBeChecked());

        await userEvent.click(canvas.getByRole('radio', { name: 'Never' }));
        await waitFor(() => expect(canvas.getByRole('radio', { name: 'Never' })).toBeChecked());
        await waitFor(() => expect(canvas.getByRole('radio', { name: 'Daily' })).not.toBeChecked());
        const radios = ['Daily', 'Weekly', 'Never'].map((name) => canvas.getByRole('radio', { name }));
        await waitFor(async () => {
            const backgrounds = radios.map((radio) => getComputedStyle(radio.nextElementSibling!).backgroundColor);
            await expect(backgrounds[0]).toBe(backgrounds[1]);
            await expect(backgrounds[2]).not.toBe(backgrounds[0]);
        });
        await expect(args.selected).toHaveBeenCalledWith(expect.objectContaining({ value: 'daily' }));
    },
};

export const Disabled: Story = {
    args: {
        disabled: true,
    },
};
