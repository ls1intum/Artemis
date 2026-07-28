import { moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';

import { TumUiButtonComponent } from '../button/tum-ui-button.component';
import { TumUiButtonGroupComponent } from './tum-ui-button-group.component';

interface ButtonGroupStoryArgs {
    firstLabel: string;
    secondLabel: string;
    thirdLabel: string;
}

const meta = {
    title: 'Actions/Button Group',
    component: TumUiButtonGroupComponent,
    subcomponents: {
        Button: TumUiButtonComponent,
    },
    decorators: [
        moduleMetadata({
            imports: [TumUiButtonComponent],
        }),
    ],
    args: {
        firstLabel: 'Previous',
        secondLabel: 'Today',
        thirdLabel: 'Next',
    },
    render: (args) => ({
        props: args,
        template: `
            <tum-ui-button-group>
                <tum-ui-button severity="secondary">{{ firstLabel }}</tum-ui-button>
                <tum-ui-button severity="secondary">{{ secondLabel }}</tum-ui-button>
                <tum-ui-button severity="secondary">{{ thirdLabel }}</tum-ui-button>
            </tum-ui-button-group>
        `,
    }),
} satisfies Meta<ButtonGroupStoryArgs>;

export default meta;

type Story = StoryObj<ButtonGroupStoryArgs>;

export const Default: Story = {};
