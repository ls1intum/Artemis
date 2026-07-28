import { argsToTemplate } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect } from 'storybook/test';

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
            <tum-ui-panel [(collapsed)]="collapsed" ${argsToTemplate(args, { exclude: ['collapsed'] })} style="display: block; width: 28rem;">
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
    play: async ({ canvas, userEvent }) => {
        const toggle = canvas.getByRole('button', { name: 'Exercise details' });
        await expect(toggle).toHaveAttribute('aria-expanded', 'true');
        await userEvent.click(toggle);
        await expect(toggle).toHaveAttribute('aria-expanded', 'false');
    },
};
