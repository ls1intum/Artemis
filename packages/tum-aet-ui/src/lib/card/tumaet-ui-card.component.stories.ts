import { argsToTemplate } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';

import { TumAetUiCardComponent } from './tumaet-ui-card.component';
import type { TumAetUiCardVariant } from './tumaet-ui-card.variants';

interface CardStoryArgs {
    variant: TumAetUiCardVariant;
    header: string;
    subheader: string;
    body: string;
    footer: string;
}

const meta = {
    title: 'Data Display/Card',
    component: TumAetUiCardComponent,
    argTypes: {
        variant: { control: 'inline-radio', options: ['elevated', 'muted'] },
    },
    args: {
        variant: 'elevated',
        header: 'Course progress',
        subheader: 'Software Engineering',
        body: 'You completed 8 of 12 exercises.',
        footer: 'Updated a few seconds ago',
    },
    render: ({ body, footer, ...componentArgs }) => ({
        props: { ...componentArgs, body, footer },
        template: `
            <tumaet-ui-card ${argsToTemplate(componentArgs)} style="display: block; width: 24rem;">
                <p style="margin: 0;">{{ body }}</p>
                <small tumAetUiCardFooter style="color: var(--tumaet-ui-muted-color);">{{ footer }}</small>
            </tumaet-ui-card>
        `,
    }),
} satisfies Meta<CardStoryArgs>;

export default meta;

type Story = StoryObj<CardStoryArgs>;

export const Default: Story = {};

/** Set into the page on the neutral fill with a border, for grouped settings. */
export const Muted: Story = {
    args: { variant: 'muted' },
};
