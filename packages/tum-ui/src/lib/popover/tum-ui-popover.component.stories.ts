import { moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect, userEvent, waitFor, within } from 'storybook/test';
import { TumUiButtonDirective } from '../button/tum-ui-button.directive';
import { TumUiPopoverTriggerDirective } from './tum-ui-popover-trigger.directive';
import { TumUiPopoverComponent } from './tum-ui-popover.component';

const meta = {
    title: 'Overlays/Popover',
    component: TumUiPopoverComponent,
    decorators: [
        moduleMetadata({
            imports: [TumUiButtonDirective, TumUiPopoverTriggerDirective],
        }),
    ],
    render: () => ({
        template: `
            <button tumUiButton [tumUiPopoverTrigger]="popover">Review details</button>
            <tum-ui-popover #popover ariaLabel="Enrollment details">
                <strong>Advanced Software Engineering</strong>
                <p>Monday, 10:00–12:00</p>
            </tum-ui-popover>
        `,
    }),
} satisfies Meta<TumUiPopoverComponent>;

export default meta;

type Story = StoryObj<TumUiPopoverComponent>;

export const Default: Story = {
    play: async ({ canvas }) => {
        const trigger = canvas.getByRole('button', { name: 'Review details' });
        await userEvent.click(trigger);

        const popover = await within(document.body).findByRole('dialog', { name: 'Enrollment details' });
        await expect(popover).toHaveFocus();
        await expect(trigger).toHaveAttribute('aria-expanded', 'true');

        await userEvent.keyboard('{Escape}');
        await waitFor(() => expect(popover).not.toBeInTheDocument());
        await expect(trigger).toHaveAttribute('aria-expanded', 'false');
    },
};
