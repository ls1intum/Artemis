import { argsToTemplate, moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect, fn, screen, waitForElementToBeRemoved } from 'storybook/test';
import { TumUiButtonDirective } from '../button/tum-ui-button.directive';
import { TumUiPopoverTriggerDirective } from './tum-ui-popover-trigger.directive';
import { TumUiPopoverComponent } from './tum-ui-popover.component';

const meta = {
    title: 'Overlays/Popover',
    component: TumUiPopoverComponent,
    subcomponents: {
        PopoverTrigger: TumUiPopoverTriggerDirective,
    },
    args: {
        ariaLabel: 'Enrollment details',
        openChange: fn(),
        placement: 'bottom',
    },
    argTypes: {
        placement: {
            control: 'inline-radio',
            options: ['top', 'right', 'bottom', 'left'],
        },
    },
    decorators: [
        moduleMetadata({
            imports: [TumUiButtonDirective, TumUiPopoverTriggerDirective],
        }),
    ],
    render: (args) => ({
        props: args,
        template: `
            <button tumUiButton [tumUiPopoverTrigger]="popover">Review details</button>
            <tum-ui-popover #popover ${argsToTemplate(args)}>
                <strong>Advanced Software Engineering</strong>
                <p>Monday, 10:00–12:00</p>
            </tum-ui-popover>
        `,
    }),
} satisfies Meta<TumUiPopoverComponent>;

export default meta;

type Story = StoryObj<TumUiPopoverComponent>;

export const Default: Story = {
    play: async ({ args, canvas, userEvent }) => {
        const trigger = canvas.getByRole('button', { name: 'Review details' });
        await userEvent.click(trigger);

        const popover = await screen.findByRole('dialog', { name: 'Enrollment details' });
        await expect(popover).toHaveFocus();
        await expect(trigger).toHaveAttribute('aria-expanded', 'true');
        await expect(args.openChange).toHaveBeenLastCalledWith(true);

        const popoverRemoved = waitForElementToBeRemoved(popover);
        await userEvent.keyboard('{Escape}');
        await popoverRemoved;
        await expect(trigger).toHaveAttribute('aria-expanded', 'false');
        await expect(args.openChange).toHaveBeenLastCalledWith(false);
        await expect(trigger).toHaveFocus();
    },
};
