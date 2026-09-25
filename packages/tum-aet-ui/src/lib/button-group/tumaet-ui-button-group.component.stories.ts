import { moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';

import { TumAetUiButtonComponent } from '../button/tumaet-ui-button.component';
import { TumAetUiButtonGroupComponent } from './tumaet-ui-button-group.component';

interface ButtonGroupStoryArgs {
    firstLabel: string;
    secondLabel: string;
    thirdLabel: string;
}

const meta = {
    title: 'Actions/Button Group',
    component: TumAetUiButtonGroupComponent,
    subcomponents: {
        Button: TumAetUiButtonComponent,
    },
    decorators: [
        moduleMetadata({
            imports: [TumAetUiButtonComponent],
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
            <tumaet-ui-button-group aria-label="Date navigation">
                <tumaet-ui-button severity="secondary">{{ firstLabel }}</tumaet-ui-button>
                <tumaet-ui-button severity="secondary">{{ secondLabel }}</tumaet-ui-button>
                <tumaet-ui-button severity="secondary">{{ thirdLabel }}</tumaet-ui-button>
            </tumaet-ui-button-group>
        `,
    }),
} satisfies Meta<ButtonGroupStoryArgs>;

export default meta;

type Story = StoryObj<ButtonGroupStoryArgs>;

export const Default: Story = {};
