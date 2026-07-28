import { faMagnifyingGlass } from '@fortawesome/free-solid-svg-icons';
import type { Meta, StoryObj } from '@storybook/angular-vite';

import { TumUiInputDirective } from '../input/tum-ui-input.directive';
import { TumUiIconFieldComponent, TumUiIconFieldPosition } from './tum-ui-icon-field.component';

interface IconFieldStoryArgs {
    label: string;
    placeholder: string;
    iconPosition: TumUiIconFieldPosition;
}

const meta = {
    title: 'Forms/Icon Field',
    component: TumUiIconFieldComponent,
    args: {
        label: 'Search courses',
        placeholder: 'Course name or identifier',
        iconPosition: 'left',
    },
    render: (args) => ({
        props: {
            label: args.label,
            placeholder: args.placeholder,
            icon: faMagnifyingGlass,
            iconPosition: args.iconPosition,
        },
        moduleMetadata: {
            imports: [TumUiInputDirective],
        },
        template: `
            <label for="course-search">{{ label }}</label>
            <tum-ui-icon-field [icon]="icon" [iconPosition]="iconPosition">
                <input id="course-search" tumUiInput type="search" [placeholder]="placeholder" />
            </tum-ui-icon-field>
        `,
    }),
} satisfies Meta<IconFieldStoryArgs>;

export default meta;

type Story = StoryObj<IconFieldStoryArgs>;

export const Default: Story = {};

export const Trailing: Story = {
    args: {
        iconPosition: 'right',
    },
};
