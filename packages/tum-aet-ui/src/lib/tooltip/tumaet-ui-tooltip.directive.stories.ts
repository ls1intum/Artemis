import { argsToTemplate, moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect, screen, waitForElementToBeRemoved } from 'storybook/test';
import { TumAetUiButtonDirective } from '../button/tumaet-ui-button.directive';
import { TumAetUiTooltipDirective } from './tumaet-ui-tooltip.directive';

interface TooltipStoryArgs {
    tumAetUiTooltip: string;
    tumAetUiTooltipPlacement: 'top' | 'right' | 'bottom' | 'left';
    showDelayMs: number;
    hideDelayMs: number;
}

const meta = {
    title: 'Overlays/Tooltip',
    component: TumAetUiTooltipDirective,
    args: {
        tumAetUiTooltip: 'Downloads the current result as a CSV file',
        tumAetUiTooltipPlacement: 'top',
        showDelayMs: 150,
        hideDelayMs: 100,
    },
    argTypes: {
        tumAetUiTooltipPlacement: {
            control: 'inline-radio',
            options: ['top', 'right', 'bottom', 'left'],
        },
    },
    decorators: [
        moduleMetadata({
            imports: [TumAetUiButtonDirective],
        }),
    ],
    render: (args) => ({
        props: args,
        template: `<button tumAetUiButton ${argsToTemplate(args)}>Export</button>`,
    }),
} satisfies Meta<TooltipStoryArgs>;

export default meta;

type Story = StoryObj<TooltipStoryArgs>;

export const Default: Story = {};

export const Open: Story = {
    tags: ['!autodocs'],
    play: async ({ canvas, userEvent }) => {
        const trigger = canvas.getByRole('button', { name: 'Export' });
        await userEvent.tab();

        const tooltip = await screen.findByRole('tooltip', { name: 'Downloads the current result as a CSV file' });
        await expect(trigger).toHaveAttribute('aria-describedby', tooltip.id);
    },
};

export const DismissesWithEscape: Story = {
    tags: ['!dev', '!autodocs'],
    play: async ({ canvas, userEvent }) => {
        await userEvent.tab();
        const tooltip = await screen.findByRole('tooltip', { name: 'Downloads the current result as a CSV file' });
        const tooltipRemoved = waitForElementToBeRemoved(tooltip);
        await userEvent.keyboard('{Escape}');
        await tooltipRemoved;
    },
};

export const Hoverable: Story = {
    tags: ['!dev', '!autodocs'],
    play: async ({ canvas, userEvent }) => {
        const trigger = canvas.getByRole('button', { name: 'Export' });
        await userEvent.hover(trigger);
        const tooltip = await screen.findByRole('tooltip', { name: 'Downloads the current result as a CSV file' });

        await userEvent.hover(tooltip);
        await expect(tooltip).toBeVisible();

        const tooltipRemoved = waitForElementToBeRemoved(tooltip);
        await userEvent.keyboard('{Escape}');
        await tooltipRemoved;
    },
};
