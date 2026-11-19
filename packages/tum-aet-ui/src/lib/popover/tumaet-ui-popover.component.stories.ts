import { argsToTemplate, moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect, fn, screen, waitFor, waitForElementToBeRemoved, within } from 'storybook/test';
import { TumAetUiButtonDirective } from '../button/tumaet-ui-button.directive';
import type { TumAetUiOverlayAlignment, TumAetUiOverlayPlacement } from '../overlay/tumaet-ui-overlay.service';
import { TumAetUiPopoverTriggerDirective } from './tumaet-ui-popover-trigger.directive';
import { TumAetUiPopoverComponent, type TumAetUiPopoverOpenOn, type TumAetUiPopoverPaddingOption } from './tumaet-ui-popover.component';

interface PopoverStoryArgs {
    alignment: TumAetUiOverlayAlignment;
    ariaLabel: string;
    openOn: TumAetUiPopoverOpenOn;
    padding: TumAetUiPopoverPaddingOption;
    openChange: (open: boolean) => void;
    placement: TumAetUiOverlayPlacement;
    triggerLabel: string;
}

const meta = {
    title: 'Overlays/Popover',
    component: TumAetUiPopoverComponent,
    args: {
        alignment: 'center',
        ariaLabel: 'Exercise actions',
        openOn: 'click',
        padding: 'default',
        openChange: fn(),
        placement: 'bottom',
        triggerLabel: 'More actions',
    },
    argTypes: {
        alignment: {
            control: 'inline-radio',
            options: ['center', 'start', 'end'] satisfies TumAetUiOverlayAlignment[],
        },
        openOn: {
            control: 'inline-radio',
            options: ['click', 'hover'] satisfies TumAetUiPopoverOpenOn[],
        },
        padding: {
            control: 'inline-radio',
            options: ['default', 'none'] satisfies TumAetUiPopoverPaddingOption[],
        },
        placement: {
            control: 'inline-radio',
            options: ['top', 'bottom', 'left', 'right'] satisfies TumAetUiOverlayPlacement[],
        },
    },
    decorators: [
        moduleMetadata({
            imports: [TumAetUiButtonDirective, TumAetUiPopoverTriggerDirective],
        }),
    ],
    // A template rather than a bare component: the contract needs both content projection and the trigger
    // directive on a separate host, neither of which Storybook can express through args alone.
    render: (args) => ({
        props: args,
        template: `
            <button tumAetUiButton [tumAetUiPopoverTrigger]="menu">{{ triggerLabel }}</button>
            <tumaet-ui-popover #menu ${argsToTemplate(args, { exclude: ['triggerLabel'] })}>
                <div class="tumaet:flex tumaet:flex-col tumaet:gap-2">
                    <button tumAetUiButton severity="secondary" variant="outlined">Edit</button>
                    <button tumAetUiButton severity="secondary" variant="outlined">Duplicate</button>
                    <button tumAetUiButton severity="danger" variant="outlined">Delete</button>
                </div>
            </tumaet-ui-popover>
        `,
    }),
} satisfies Meta<PopoverStoryArgs>;

export default meta;

type Story = StoryObj<PopoverStoryArgs>;

export const Default: Story = {};

export const NoPadding: Story = {
    args: {
        ariaLabel: 'No-padding popover',
        padding: 'none',
        triggerLabel: 'Show No-Padding Popover',
    },
    render: (args) => ({
        props: args,
        template: `
            <button tumAetUiButton [tumAetUiPopoverTrigger]="menu">{{ triggerLabel }}</button>
            <tumaet-ui-popover #menu ${argsToTemplate(args, { exclude: ['triggerLabel'] })}>
                <div class="tumaet:w-72">
                    <div class="tumaet:flex tumaet:items-center tumaet:justify-center tumaet:py-6">Area 1</div>
                    <div class="tumaet:border-t tumaet:border-border"></div>
                    <div class="tumaet:flex tumaet:items-center tumaet:justify-center tumaet:py-6">Area 2</div>
                </div>
            </tumaet-ui-popover>
        `,
    }),
};

export const OpensAndTrapsFocus: Story = {
    tags: ['!dev', '!autodocs'],
    play: async ({ args, canvas, userEvent }) => {
        const trigger = canvas.getByRole('button', { name: 'More actions' });
        await expect(trigger).toHaveAttribute('aria-expanded', 'false');

        await userEvent.click(trigger);

        const panel = await screen.findByRole('dialog', { name: 'Exercise actions' });
        await expect(trigger).toHaveAttribute('aria-expanded', 'true');
        await expect(panel).toContainElement(document.activeElement as HTMLElement);
        // Waited for: the panel grows into place from transparent, so it is not yet visible on the frame it opens.
        await waitFor(() => expect(within(panel).getByRole('button', { name: 'Delete' })).toBeVisible());
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

/**
 * A companion of a trigger that keeps its own click action: the popover opens while the trigger is hovered or
 * focused and does not block the page.
 */
export const OpensOnHover: Story = {
    args: {
        ariaLabel: 'Notification options',
        openOn: 'hover',
        triggerLabel: 'Mute notifications',
    },
    render: (args) => ({
        props: args,
        template: `
            <button tumAetUiButton [tumAetUiPopoverTrigger]="options">{{ triggerLabel }}</button>
            <tumaet-ui-popover #options ${argsToTemplate(args, { exclude: ['triggerLabel'] })}>
                <button tumAetUiButton severity="secondary" variant="outlined">Mute for one hour</button>
            </tumaet-ui-popover>
        `,
    }),
    play: async ({ args, canvas, userEvent }) => {
        const trigger = canvas.getByRole('button', { name: 'Mute notifications' });

        await userEvent.hover(trigger);
        const panel = await screen.findByRole('dialog', { name: 'Notification options' });
        await expect(trigger).toHaveAttribute('aria-expanded', 'true');
        await expect(args.openChange).toHaveBeenCalledWith(true);

        // The pointer may cross onto the panel without the popover closing.
        const option = within(panel).getByRole('button', { name: 'Mute for one hour' });
        await userEvent.hover(option);
        await waitFor(() => expect(option).toBeVisible());

        const panelRemoved = waitForElementToBeRemoved(panel);
        await userEvent.keyboard('{Escape}');
        await panelRemoved;
        await expect(trigger).toHaveAttribute('aria-expanded', 'false');
    },
};
