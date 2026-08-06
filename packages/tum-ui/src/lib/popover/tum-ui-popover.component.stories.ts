import { argsToTemplate, moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect, fn, screen, waitForElementToBeRemoved, within } from 'storybook/test';
import { TumUiButtonDirective } from '../button/tum-ui-button.directive';
import type { TumUiOverlayPlacement } from '../overlay/tum-ui-overlay.service';
import { TumUiPopoverTriggerDirective } from './tum-ui-popover-trigger.directive';
import { TumUiPopoverComponent } from './tum-ui-popover.component';

interface PopoverStoryArgs {
    ariaLabel: string;
    openChange: (open: boolean) => void;
    placement: TumUiOverlayPlacement;
    triggerLabel: string;
}

const meta = {
    title: 'Overlays/Popover',
    component: TumUiPopoverComponent,
    args: {
        ariaLabel: 'Exercise actions',
        openChange: fn(),
        placement: 'bottom',
        triggerLabel: 'More actions',
    },
    argTypes: {
        placement: {
            control: 'inline-radio',
            options: ['top', 'bottom', 'left', 'right'] satisfies TumUiOverlayPlacement[],
        },
    },
    decorators: [
        moduleMetadata({
            imports: [TumUiButtonDirective, TumUiPopoverTriggerDirective],
        }),
    ],
    // A template rather than a bare component: the contract needs both content projection and the trigger
    // directive on a separate host, neither of which Storybook can express through args alone.
    render: (args) => ({
        props: args,
        template: `
            <button tumUiButton [tumUiPopoverTrigger]="menu">{{ triggerLabel }}</button>
            <tum-ui-popover #menu ${argsToTemplate(args, { exclude: ['triggerLabel'] })}>
                <div class="tum:flex tum:flex-col tum:gap-2">
                    <button tumUiButton severity="secondary" variant="outlined">Edit</button>
                    <button tumUiButton severity="secondary" variant="outlined">Duplicate</button>
                    <button tumUiButton severity="danger" variant="outlined">Delete</button>
                </div>
            </tum-ui-popover>
        `,
    }),
} satisfies Meta<PopoverStoryArgs>;

export default meta;

type Story = StoryObj<PopoverStoryArgs>;

export const Default: Story = {};

export const OpensAndTrapsFocus: Story = {
    tags: ['!dev', '!autodocs'],
    play: async ({ args, canvas, userEvent }) => {
        const trigger = canvas.getByRole('button', { name: 'More actions' });
        await expect(trigger).toHaveAttribute('aria-expanded', 'false');

        await userEvent.click(trigger);

        const panel = await screen.findByRole('dialog', { name: 'Exercise actions' });
        await expect(trigger).toHaveAttribute('aria-expanded', 'true');
        await expect(panel).toContainElement(document.activeElement as HTMLElement);
        await expect(within(panel).getByRole('button', { name: 'Delete' })).toBeVisible();
        await expect(args.openChange).toHaveBeenCalledWith(true);
    },
};

export const ClosesOnEscape: Story = {
    tags: ['!dev', '!autodocs'],
    play: async ({ args, canvas, userEvent }) => {
        await userEvent.click(canvas.getByRole('button', { name: 'More actions' }));

        const panel = await screen.findByRole('dialog', { name: 'Exercise actions' });
        const panelRemoved = waitForElementToBeRemoved(panel);
        await userEvent.keyboard('{Escape}');
        await panelRemoved;

        await expect(args.openChange).toHaveBeenLastCalledWith(false);
        await expect(canvas.getByRole('button', { name: 'More actions' })).toHaveAttribute('aria-expanded', 'false');
    },
};
