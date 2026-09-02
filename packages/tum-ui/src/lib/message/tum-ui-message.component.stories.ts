import { faCircleInfo } from '@fortawesome/free-solid-svg-icons';
import { moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect } from 'storybook/test';

import { TumUiButtonComponent } from '../button/tum-ui-button.component';
import { TumUiMessageComponent, TumUiMessageSeverity } from './tum-ui-message.component';

const severities: TumUiMessageSeverity[] = ['info', 'success', 'warning', 'danger', 'secondary', 'contrast'];
const meta = {
    title: 'Feedback/Message',
    component: TumUiMessageComponent,
    decorators: [moduleMetadata({ imports: [TumUiMessageComponent, TumUiButtonComponent] })],
    args: {
        text: 'The exercise opens tomorrow.',
        severity: 'info',
        icon: faCircleInfo,
        live: false,
    },
    argTypes: {
        icon: {
            control: false,
        },
        severity: {
            control: 'select',
            options: severities,
        },
    },
} satisfies Meta<TumUiMessageComponent>;

export default meta;

type Story = StoryObj<TumUiMessageComponent>;

export const Default: Story = {};

export const Success: Story = {
    args: {
        icon: undefined,
        severity: 'success',
        text: 'Your changes have been saved.',
    },
};

export const Warning: Story = {
    args: {
        icon: undefined,
        severity: 'warning',
        text: 'The submission deadline is approaching.',
    },
};

export const Error: Story = {
    args: {
        icon: undefined,
        severity: 'danger',
        text: 'The submission could not be uploaded.',
    },
};

export const Secondary: Story = {
    args: {
        icon: undefined,
        severity: 'secondary',
        text: 'No assessment is available yet.',
    },
};

export const Contrast: Story = {
    args: {
        icon: undefined,
        severity: 'contrast',
        text: 'Maintenance starts at 22:00.',
    },
};

/**
 * A message with the control that resolves it. `text` and projected content render in the same line, so a sentence
 * and its remedy no longer force the consumer to abandon `text` entirely.
 */
export const WithAnAction: Story = {
    args: {
        icon: undefined,
        severity: 'danger',
        text: 'The run could not be started.',
    },
    render: (args) => ({
        props: args,
        template: `
            <tum-ui-message [severity]="severity" [text]="text">
                &nbsp;<tum-ui-button size="small" variant="outlined" severity="danger">Try again</tum-ui-button>
            </tum-ui-message>
        `,
    }),
};

/**
 * Live is opt-in, and there is at most one per surface. Five permanently-rendered messages would otherwise be five
 * live regions competing to announce themselves on load — and an error rendered *with* the page announces nothing
 * at all, because a live region only fires on a change after it already exists.
 */
export const LiveRegion: Story = {
    args: {
        icon: undefined,
        severity: 'success',
        text: 'Your changes have been saved.',
        live: true,
    },
};

/** Four static messages on one page: none of them is a live region, and none of them announces itself. */
export const SeveralStaticMessages: Story = {
    render: () => ({
        props: { severities },
        template: `
            <div style="display: grid; gap: 0.5rem; width: 28rem;">
                @for (severity of severities; track severity) {
                    <tum-ui-message [severity]="severity" [text]="severity" />
                }
            </div>
        `,
    }),
};

/** Measured proof of the live-region contract, which is invisible in a screenshot and goes wrong quietly. */
export const LiveRegionContract: Story = {
    tags: ['!dev', '!autodocs'],
    render: () => ({
        template: `
            <div style="display: grid; gap: 0.5rem; width: 28rem;">
                <tum-ui-message severity="info" text="Static, and silent." />
                <tum-ui-message severity="success" text="Announced politely." live />
                <tum-ui-message severity="danger" text="Announced assertively." live />
            </div>
        `,
    }),
    play: async ({ canvasElement }) => {
        const messages = [...canvasElement.querySelectorAll('tum-ui-message')];
        await expect(messages[0].getAttribute('role'), 'a static message is not a live region').toBeNull();
        await expect(messages[1].getAttribute('role')).toBe('status');
        await expect(messages[2].getAttribute('role'), 'the failure interrupts, the rest does not').toBe('alert');
    },
};
