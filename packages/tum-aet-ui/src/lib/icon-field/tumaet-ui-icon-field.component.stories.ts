import { faMagnifyingGlass } from '@fortawesome/free-solid-svg-icons';
import { moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';

import { formStoryDecorator } from '../../../.storybook/story-decorators';
import { TumAetUiInputDirective } from '../input/tumaet-ui-input.directive';
import { TumAetUiIconFieldComponent, TumAetUiIconFieldPosition } from './tumaet-ui-icon-field.component';

interface IconFieldStoryArgs {
    label: string;
    placeholder: string;
    iconPosition: TumAetUiIconFieldPosition;
}

const meta = {
    title: 'Forms/Icon Field',
    component: TumAetUiIconFieldComponent,
    args: {
        label: 'Search courses',
        placeholder: 'Course name or identifier',
        iconPosition: 'left',
    },
    decorators: [
        formStoryDecorator,
        moduleMetadata({
            imports: [TumAetUiInputDirective],
        }),
    ],
    render: (args) => ({
        props: {
            label: args.label,
            placeholder: args.placeholder,
            icon: faMagnifyingGlass,
            iconPosition: args.iconPosition,
        },
        template: `
            <label for="course-search">{{ label }}</label>
            <tumaet-ui-icon-field [icon]="icon" [iconPosition]="iconPosition">
                <input id="course-search" tumAetUiInput type="search" [placeholder]="placeholder" />
            </tumaet-ui-icon-field>
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
