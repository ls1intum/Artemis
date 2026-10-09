import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect } from 'storybook/test';

import { formStoryDecorator } from '../../../.storybook/story-decorators';
import { TumAetUiInputDirective } from './tumaet-ui-input.directive';
import { TumAetUiInputSize } from './tumaet-ui-input.variants';

interface InputStoryArgs {
    label: string;
    placeholder: string;
    tumAetUiInputSize: TumAetUiInputSize | undefined;
    tumAetUiInputInvalid: boolean;
    disabled: boolean;
}

const meta = {
    title: 'Forms/Input',
    component: TumAetUiInputDirective,
    args: {
        label: 'Course title',
        placeholder: 'Introduction to Computer Science',
        tumAetUiInputSize: undefined,
        tumAetUiInputInvalid: false,
        disabled: false,
    },
    argTypes: {
        tumAetUiInputSize: {
            control: 'inline-radio',
            options: [undefined, 'small', 'large'],
        },
    },
    decorators: [formStoryDecorator],
    render: (args) => ({
        props: args,
        template: `
            <label for="course-title">{{ label }}</label>
            <input
                id="course-title"
                tumAetUiInput
                [placeholder]="placeholder"
                [tumAetUiInputSize]="tumAetUiInputSize"
                [tumAetUiInputInvalid]="tumAetUiInputInvalid"
                [disabled]="disabled"
                [attr.aria-invalid]="tumAetUiInputInvalid || null"
                [attr.aria-describedby]="tumAetUiInputInvalid ? 'course-title-error' : null"
            />
            @if (tumAetUiInputInvalid) {
                <div id="course-title-error" class="tumaet-ui-story-error" role="alert">Enter a course title.</div>
            }
        `,
    }),
} satisfies Meta<InputStoryArgs>;

export default meta;

type Story = StoryObj<InputStoryArgs>;

export const Default: Story = {};

export const Invalid: Story = {
    args: {
        tumAetUiInputInvalid: true,
    },
    play: async ({ canvas, userEvent }) => {
        await userEvent.tab();
        const input = canvas.getByRole('textbox', { name: 'Course title' });
        await expect(input).toHaveFocus();
        await expect(getComputedStyle(input).outlineWidth).toBe('2px');
    },
};

export const Disabled: Story = {
    args: {
        disabled: true,
    },
};

export const Multiline: Story = {
    args: {
        label: 'Course description',
        placeholder: 'Describe the course',
    },
    render: (args) => ({
        props: args,
        template: `
            <label for="course-description">{{ label }}</label>
            <textarea
                id="course-description"
                tumAetUiTextarea
                rows="4"
                [placeholder]="placeholder"
                [tumAetUiInputSize]="tumAetUiInputSize"
                [tumAetUiInputInvalid]="tumAetUiInputInvalid"
                [disabled]="disabled"
                [attr.aria-invalid]="tumAetUiInputInvalid || null"
                [attr.aria-describedby]="tumAetUiInputInvalid ? 'course-description-error' : null"
            ></textarea>
            @if (tumAetUiInputInvalid) {
                <div id="course-description-error" class="tumaet-ui-story-error" role="alert">Enter a course description.</div>
            }
        `,
    }),
};
