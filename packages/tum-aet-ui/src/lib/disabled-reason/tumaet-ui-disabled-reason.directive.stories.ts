import { argsToTemplate, moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect, screen } from 'storybook/test';
import { TumAetUiButtonDirective } from '../button/tumaet-ui-button.directive';
import { TumAetUiDisabledReasonDirective } from './tumaet-ui-disabled-reason.directive';

interface DisabledReasonStoryArgs {
    tumAetUiDisabledReason: string;
}

const meta = {
    title: 'Actions/Disabled Reason',
    component: TumAetUiDisabledReasonDirective,
    args: {
        tumAetUiDisabledReason: 'Save the exercise before releasing it',
    },
    decorators: [
        moduleMetadata({
            imports: [TumAetUiButtonDirective],
        }),
    ],
    render: (args) => ({
        props: args,
        template: `<button tumAetUiButton ${argsToTemplate(args)}>Release</button>`,
    }),
} satisfies Meta<DisabledReasonStoryArgs>;

export default meta;

type Story = StoryObj<DisabledReasonStoryArgs>;

/** Looks disabled, but stays in the tab order and explains itself on hover and focus. Clear the reason to re-enable. */
export const Default: Story = {};

export const Explained: Story = {
    tags: ['!autodocs'],
    play: async ({ canvas, userEvent }) => {
        const button = canvas.getByRole('button', { name: 'Release' });
        await expect(button).toHaveAttribute('aria-disabled', 'true');
        await userEvent.tab();
        await expect(button).toHaveFocus();
        await screen.findByRole('tooltip', { name: 'Save the exercise before releasing it' });
    },
};

export const Enabled: Story = {
    args: { tumAetUiDisabledReason: '' },
};
