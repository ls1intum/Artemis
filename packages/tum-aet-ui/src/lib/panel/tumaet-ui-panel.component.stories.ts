import { argsToTemplate } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { TumAetUiPanelComponent } from './tumaet-ui-panel.component';

interface PanelStoryArgs {
    header: string;
    content: string;
    toggleable: boolean;
    collapsed: boolean;
}

const meta = {
    title: 'Data Display/Panel',
    component: TumAetUiPanelComponent,
    args: {
        header: 'Exercise details',
        content: 'Review the problem statement, due date, and grading criteria.',
        toggleable: false,
        collapsed: false,
    },
    render: ({ content, ...args }) => {
        return {
            props: {
                ...args,
                content,
            },
            template: `
                <tumaet-ui-panel
                    [(collapsed)]="collapsed"
                    ${argsToTemplate(args, { exclude: ['collapsed'] })}
                    style="display: block; width: min(28rem, 100%);"
                >
                    <p style="margin: 0;">{{ content }}</p>
                </tumaet-ui-panel>
            `,
        };
    },
} satisfies Meta<PanelStoryArgs>;

export default meta;

type Story = StoryObj<PanelStoryArgs>;

export const Default: Story = {};

export const Toggleable: Story = {
    args: {
        toggleable: true,
    },
};

export const Collapsed: Story = {
    args: {
        collapsed: true,
        toggleable: true,
    },
};
