import type { Meta, StoryObj } from '@storybook/angular-vite';

import { TumUiCardComponent } from './tum-ui-card.component';

interface CardStoryArgs {
    header: string;
    subheader: string;
    body: string;
    footer: string;
}

const meta = {
    title: 'Data Display/Card',
    component: TumUiCardComponent,
    args: {
        header: 'Course progress',
        subheader: 'Software Engineering',
        body: 'You completed 8 of 12 exercises.',
        footer: 'Updated a few seconds ago',
    },
    render: ({ body, footer, ...args }) => ({
        props: { ...args, body, footer },
        template: `
            <tum-ui-card [header]="header" [subheader]="subheader" style="display: block; width: 24rem;">
                <p style="margin: 0;">{{ body }}</p>
                <small tumUiCardFooter style="color: var(--tum-ui-muted-color);">{{ footer }}</small>
            </tum-ui-card>
        `,
    }),
} satisfies Meta<CardStoryArgs>;

export default meta;

type Story = StoryObj<CardStoryArgs>;

export const Default: Story = {};
