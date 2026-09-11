import { argsToTemplate, moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect, screen } from 'storybook/test';
import { TumUiButtonDirective } from '../button/tum-ui-button.directive';
import { TumUiDisabledReasonDirective } from './tum-ui-disabled-reason.directive';

interface DisabledReasonStoryArgs {
    tumUiDisabledReason: string;
}

const meta = {
    title: 'Actions/Disabled Reason',
    component: TumUiDisabledReasonDirective,
    args: {
        tumUiDisabledReason: 'Save the exercise before releasing it',
    },
    decorators: [
        moduleMetadata({
            imports: [TumUiButtonDirective],
        }),
    ],
    render: (args) => ({
        props: args,
        template: `<button tumUiButton ${argsToTemplate(args)}>Release</button>`,
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
    args: { tumUiDisabledReason: '' },
};
