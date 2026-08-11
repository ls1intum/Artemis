import { FormsModule } from '@angular/forms';
import { moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect, fn, waitFor } from 'storybook/test';

import { formStoryDecorator } from '../../../.storybook/story-decorators';
import { TumUiRadioButtonComponent, TumUiRadioButtonSelectEvent } from './tum-ui-radio-button.component';

interface RadioOption {
    label: string;
    value: string;
}

interface RadioButtonStoryArgs {
    groupId: string;
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
        formStoryDecorator,
        moduleMetadata({
            imports: [FormsModule],
        }),
    ],
    args: {
        groupId: 'email-summary-default',
        legend: 'Email summary',
        options: OPTIONS,
        selectedValue: 'weekly',
        disabled: false,
        selected: fn(),
    },
    argTypes: {
        groupId: {
            table: { disable: true },
        },
        options: {
            control: false,
        },
        selectedValue: {
            control: 'inline-radio',
            options: OPTIONS.map((option) => option.value),
        },
        selected: {
            control: false,
        },
    },
    render: (args) => {
        return {
            props: { ...args },
            template: `
                <form>
                    <fieldset>
                        <legend>{{ legend }}</legend>
                        @for (option of options; track option.value) {
                            <label [for]="groupId + '-' + option.value">
                                <tum-ui-radio-button
                                    [inputId]="groupId + '-' + option.value"
                                    [name]="groupId"
                                    [value]="option.value"
                                    [ngModel]="selectedValue"
                                    (ngModelChange)="selectedValue = $event"
                                    [ngModelOptions]="{ standalone: true }"
                                    [disabled]="disabled"
                                    (selected)="selected($event)"
                                />
                                {{ option.label }}
                            </label>
                        }
                    </fieldset>
                </form>
            `,
        };
    },
} satisfies Meta<RadioButtonStoryArgs>;

export default meta;

type Story = StoryObj<RadioButtonStoryArgs>;

export const Default: Story = {};

export const Selection: Story = {
    args: {
        groupId: 'email-summary-selection',
    },
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
        groupId: 'email-summary-disabled',
        disabled: true,
    },
};

/**
 * A radio button keeps its round shape when the row around it runs out of room.
 * <p>
 * Flex items shrink by default, and the control has a fixed height but no shrink floor, so a long label in a narrow row
 * used to squeeze it into an oval. Zooming the page reproduces it the same way, because that is what shrinks the space
 * the label has to fit in. The row below is deliberately too narrow for its label.
 */
export const StaysRoundInATightRow: Story = {
    tags: ['!dev', '!autodocs'],
    render: (args) => ({
        props: { ...args },
        template: `
            <div style="display: flex; align-items: center; gap: 8px; width: 120px;">
                <tum-ui-radio-button inputId="tight" name="tight" value="tight" data-testid="tight-radio-button" />
                <label for="tight">A label far too long to fit beside the control in this row</label>
            </div>
        `,
    }),
    play: async ({ canvas }) => {
        const radioButton = canvas.getByTestId('tight-radio-button');
        const { width, height } = radioButton.getBoundingClientRect();

        await expect(width).toBeGreaterThan(0);
        await expect(width).toBeCloseTo(height, 1);
    },
};
