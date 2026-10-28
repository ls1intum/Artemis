import { moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect, fireEvent } from 'storybook/test';
import { TumAetUiSplitterComponent, TumAetUiSplitterPanelDirective } from './tumaet-ui-splitter.component';

const meta = {
    title: 'Layout/Splitter',
    component: TumAetUiSplitterComponent,
    decorators: [moduleMetadata({ imports: [TumAetUiSplitterPanelDirective] })],
    args: {
        orientation: 'horizontal',
        sizes: [60, 40],
        minSizes: [20, 20],
        gutterSize: 8,
        separatorLabel: 'Resize document and notes',
    },
    argTypes: {
        orientation: { control: 'inline-radio', options: ['horizontal', 'vertical'] },
        sizesChange: { control: false },
        resizeEnd: { control: false },
    },
    render: (args) => ({
        props: { ...args },
        template: `
            <tumaet-ui-splitter [orientation]="orientation" [sizes]="sizes" [minSizes]="minSizes" [gutterSize]="gutterSize" [separatorLabel]="separatorLabel" style="height: 20rem; width: 100%;">
                <ng-template tumAetUiSplitterPanel>
                    <article style="padding: 1rem; overflow: auto;"><h2>Document</h2><p>Drag the separator or use the arrow keys to make room for notes.</p></article>
                </ng-template>
                <ng-template tumAetUiSplitterPanel>
                    <label style="padding: 1rem;">Notes<textarea aria-label="Notes" style="display: block; max-width: 100%;">Keep this draft while resizing.</textarea></label>
                </ng-template>
            </tumaet-ui-splitter>
        `,
    }),
} satisfies Meta<TumAetUiSplitterComponent>;

export default meta;
type Story = StoryObj<TumAetUiSplitterComponent>;

export const Default: Story = {};
export const Vertical: Story = { args: { orientation: 'vertical' } };
export const KeyboardResize: Story = {
    play: async ({ canvas, userEvent }) => {
        const separator = canvas.getByRole('separator', { name: 'Resize document and notes' });
        await userEvent.click(separator);
        await fireEvent.keyDown(separator, { key: 'Home' });
        await expect(separator).toHaveFocus();
        await expect(separator).toHaveAttribute('aria-valuenow', '20');
        await fireEvent.keyDown(separator, { key: 'End' });
        await expect(separator).toHaveAttribute('aria-valuenow', '80');
        await fireEvent.keyDown(separator, { key: 'ArrowLeft' });
        await expect(separator).toHaveAttribute('aria-valuenow', '79');
    },
};
