import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect, userEvent, within } from 'storybook/test';
import { TumAetUiSplitterComponent } from './tumaet-ui-splitter.component';

const meta = {
    title: 'Layout/Splitter',
    component: TumAetUiSplitterComponent,
    args: { layout: 'horizontal', panelSizes: [60, 40], minSizes: [20, 20], ariaLabel: 'Resize workspace panels', collapsedSecondSize: undefined, gutterSize: undefined },
    render: (args) => ({
        props: args,
        template: `<div style="height: 24rem; width: 100%;">
            <tumaet-ui-splitter [layout]="layout" [panelSizes]="panelSizes" [minSizes]="minSizes" [ariaLabel]="ariaLabel" [gutterSize]="gutterSize" [collapsedSecondSize]="collapsedSecondSize">
                <ng-template #panel><section style="padding: 1rem;"><h2>Workspace</h2><p>Drag or focus the divider to resize the panels.</p><button type="button">Edit submission</button></section></ng-template>
                <ng-template #panel><section style="padding: 1rem;"><h2>Instructions</h2><p>Both panels retain their contents while resizing.</p></section></ng-template>
            </tumaet-ui-splitter>
        </div>`,
    }),
} satisfies Meta<TumAetUiSplitterComponent>;

export default meta;
type Story = StoryObj<TumAetUiSplitterComponent>;

export const Default: Story = {
    play: async ({ canvasElement }) => {
        const separator = within(canvasElement).getByRole('separator', { name: 'Resize workspace panels' });
        separator.focus();
        await userEvent.keyboard('{ArrowRight}');
        await expect(separator).toHaveAttribute('aria-valuenow', '62');
    },
};

export const Vertical: Story = { args: { layout: 'vertical' } };

export const CollapsedSecondPanel: Story = { args: { collapsedSecondSize: '3rem' } };
