import { moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect, userEvent, waitFor, within } from 'storybook/test';
import { TumUiButtonDirective } from '../button/tum-ui-button.directive';
import { TumUiTooltipDirective } from './tum-ui-tooltip.directive';

const meta = {
    title: 'Overlays/Tooltip',
    decorators: [
        moduleMetadata({
            imports: [TumUiButtonDirective, TumUiTooltipDirective],
        }),
    ],
    render: () => ({
        template: `
            <button tumUiButton tumUiTooltip="Downloads the current result as a CSV file" [showDelay]="0" [hideDelay]="0">
                Export
            </button>
        `,
    }),
} satisfies Meta;

export default meta;

type Story = StoryObj<typeof meta>;

export const Default: Story = {
    play: async ({ canvas }) => {
        const trigger = canvas.getByRole('button', { name: 'Export' });
        await userEvent.tab();

        const tooltip = await within(document.body).findByRole('tooltip');
        await waitFor(() => expect(tooltip).toHaveTextContent('Downloads the current result as a CSV file'));
        await expect(trigger).toHaveAttribute('aria-describedby', tooltip.id);

        await userEvent.keyboard('{Escape}');
        await waitFor(() => expect(tooltip).not.toBeInTheDocument());
    },
};
