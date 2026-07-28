import type { Meta, StoryObj } from '@storybook/angular-vite';

import { TumUiInputDirective } from './tum-ui-input.directive';
import { TumUiInputSize } from './tum-ui-input.variants';

interface InputStoryArgs {
    label: string;
    placeholder: string;
    size: TumUiInputSize | undefined;
    invalid: boolean;
    disabled: boolean;
}

const meta = {
    title: 'Forms/Input',
    component: TumUiInputDirective,
    args: {
        label: 'Course title',
        placeholder: 'Introduction to Computer Science',
        size: undefined,
        invalid: false,
        disabled: false,
    },
    render: (args) => ({
        props: args,
        template: `
            <label for="course-title">{{ label }}</label>
            <input
                id="course-title"
                tumUiInput
                [placeholder]="placeholder"
                [tumUiInputSize]="size"
                [tumUiInputInvalid]="invalid"
                [disabled]="disabled"
                [attr.aria-invalid]="invalid || null"
                [attr.aria-describedby]="invalid ? 'course-title-error' : null"
            />
            @if (invalid) {
                <div id="course-title-error">Enter a course title.</div>
            }
        `,
    }),
} satisfies Meta<InputStoryArgs>;

export default meta;

type Story = StoryObj<InputStoryArgs>;

export const Default: Story = {};

export const Invalid: Story = {
    args: {
        invalid: true,
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
                tumUiTextarea
                rows="4"
                [placeholder]="placeholder"
                [tumUiInputSize]="size"
                [tumUiInputInvalid]="invalid"
                [disabled]="disabled"
            ></textarea>
        `,
    }),
};

export const DarkTheme: Story = {
    ...Default,
    globals: {
        theme: 'dark',
    },
};
