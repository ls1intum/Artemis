import type { Meta, StoryObj } from '@storybook/angular-vite';

import { TumUiPanelComponent } from './tum-ui-panel.component';

interface PanelStoryArgs {
    header: string;
    content: string;
    toggleable: boolean;
    collapsed: boolean;
}

const meta = {
    title: 'Data Display/Panel',
    component: TumUiPanelComponent,
    args: {
        header: 'Exercise details',
        content: 'Review the problem statement, due date, and grading criteria.',
        toggleable: false,
        collapsed: false,
    },
    render: ({ content, ...args }) => ({
        props: { ...args, content },
        template: `
            <tum-ui-panel [header]="header" [toggleable]="toggleable" [(collapsed)]="collapsed" style="display: block; width: 28rem;">
                <p style="margin: 0;">{{ content }}</p>
            </tum-ui-panel>
        `,
    }),
} satisfies Meta<PanelStoryArgs>;

export default meta;

type Story = StoryObj<PanelStoryArgs>;

export const Default: Story = {};

export const Toggleable: Story = {
    args: {
        toggleable: true,
    },
};
