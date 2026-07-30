import { moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect, screen, waitForElementToBeRemoved } from 'storybook/test';
import { TumUiButtonDirective } from '../button/tum-ui-button.directive';
import { TumUiTooltipDirective } from './tum-ui-tooltip.directive';

interface TooltipStoryArgs {
    tumUiTooltip: string;
    tumUiTooltipPlacement: 'top' | 'right' | 'bottom' | 'left';
    showDelay: number;
    hideDelay: number;
}

const meta = {
    title: 'Overlays/Tooltip',
    component: TumUiTooltipDirective,
    args: {
        tumUiTooltip: 'Downloads the current result as a CSV file',
        tumUiTooltipPlacement: 'top',
        showDelay: 150,
        hideDelay: 100,
    },
    argTypes: {
        tumUiTooltipPlacement: {
            control: 'inline-radio',
            options: ['top', 'right', 'bottom', 'left'],
        },
    },
    decorators: [
        moduleMetadata({
            imports: [TumUiButtonDirective, TumUiTooltipDirective],
        }),
    ],
    render: (args) => ({
        props: { tooltip: args },
        template: `
            <button
                tumUiButton
                [tumUiTooltip]="tooltip.tumUiTooltip"
                [tumUiTooltipPlacement]="tooltip.tumUiTooltipPlacement"
                [showDelay]="tooltip.showDelay"
                [hideDelay]="tooltip.hideDelay"
            >
                Export
            </button>
        `,
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
