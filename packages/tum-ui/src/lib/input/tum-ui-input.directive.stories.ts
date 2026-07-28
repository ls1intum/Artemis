import type { Meta, StoryObj } from '@storybook/angular-vite';

import { TumUiInputDirective } from './tum-ui-input.directive';
import { TumUiInputSize } from './tum-ui-input.variants';

interface InputStoryArgs {
    label: string;
    placeholder: string;
    tumUiInputSize: TumUiInputSize | undefined;
    tumUiInputInvalid: boolean;
    disabled: boolean;
}

const meta = {
    title: 'Forms/Input',
    component: TumUiInputDirective,
    args: {
        label: 'Course title',
        placeholder: 'Introduction to Computer Science',
        tumUiInputSize: undefined,
        tumUiInputInvalid: false,
        disabled: false,
    },
    argTypes: {
        tumUiInputSize: {
            control: 'inline-radio',
            options: [undefined, 'small', 'large'],
        },
    },
    render: (args) => ({
        props: args,
        template: `
            <label for="course-title">{{ label }}</label>
            <input
                id="course-title"
                tumUiInput
                [placeholder]="placeholder"
                [tumUiInputSize]="tumUiInputSize"
                [tumUiInputInvalid]="tumUiInputInvalid"
                [disabled]="disabled"
                [attr.aria-invalid]="tumUiInputInvalid || null"
                [attr.aria-describedby]="tumUiInputInvalid ? 'course-title-error' : null"
            />
            @if (tumUiInputInvalid) {
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
        tumUiInputInvalid: true,
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
                [tumUiInputSize]="tumUiInputSize"
                [tumUiInputInvalid]="tumUiInputInvalid"
                [disabled]="disabled"
                [attr.aria-invalid]="tumUiInputInvalid || null"
                [attr.aria-describedby]="tumUiInputInvalid ? 'course-description-error' : null"
            ></textarea>
            @if (tumUiInputInvalid) {
                <div id="course-description-error">Enter a course description.</div>
            }
        `,
    }),
};
