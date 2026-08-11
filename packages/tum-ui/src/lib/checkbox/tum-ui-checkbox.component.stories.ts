import { argsToTemplate } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect, fn } from 'storybook/test';

import { inlineControlStoryDecorator } from '../../../.storybook/story-decorators';
import { TumUiCheckboxChangeEvent, TumUiCheckboxComponent } from './tum-ui-checkbox.component';

interface CheckboxStoryArgs {
    label: string;
    checked: boolean;
    disabled: boolean;
    changed: (event: TumUiCheckboxChangeEvent) => void;
}

const meta = {
    title: 'Forms/Checkbox',
    component: TumUiCheckboxComponent,
    args: {
        label: 'Accept the terms and conditions',
        checked: false,
        disabled: false,
        changed: fn(),
    },
    argTypes: {
        changed: { control: false },
    },
    decorators: [inlineControlStoryDecorator],
    render: (args) => {
        return {
            props: { ...args },
            template: `
                <label for="terms">
                    <tum-ui-checkbox
                        inputId="terms"
                        name="terms"
                        [checked]="checked"
                        ${argsToTemplate(args, { exclude: ['checked', 'label', 'changed'] })}
                        (changed)="checked = $event.checked; changed($event)"
                    />
                    {{ label }}
                </label>
            `,
        };
    },
} satisfies Meta<CheckboxStoryArgs>;

export default meta;

type Story = StoryObj<CheckboxStoryArgs>;

export const Default: Story = {};

export const Checked: Story = {
    args: {
        checked: true,
    },
};

export const Disabled: Story = {
    args: {
        checked: true,
        disabled: true,
    },
};

/**
 * A checkbox keeps its square shape when the row around it runs out of room.
 * <p>
 * Flex items shrink by default, and the box has a fixed height but no shrink floor, so a long label in a narrow row
 * used to squeeze it into a rectangle. Zooming the page reproduces it the same way, because that is what shrinks the
 * space the label has to fit in. The row below is deliberately too narrow for its label.
 */
export const StaysSquareInATightRow: Story = {
    tags: ['!dev', '!autodocs'],
    render: (args) => ({
        props: { ...args },
        template: `
            <div style="display: flex; align-items: center; gap: 8px; width: 120px;">
                <tum-ui-checkbox inputId="tight" name="tight" data-testid="tight-checkbox" />
                <label for="tight">A label far too long to fit beside the box in this row</label>
            </div>
        `,
    }),
    play: async ({ canvas }) => {
        const checkbox = canvas.getByTestId('tight-checkbox');
        const { width, height } = checkbox.getBoundingClientRect();

        await expect(width).toBeGreaterThan(0);
        await expect(width).toBeCloseTo(height, 1);
    },
};
