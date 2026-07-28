import { faCircleInfo } from '@fortawesome/free-solid-svg-icons';
import type { Meta, StoryObj } from '@storybook/angular-vite';

import { TumUiMessageComponent, TumUiMessageSeverity } from './tum-ui-message.component';

const severities: TumUiMessageSeverity[] = ['info', 'success', 'warn', 'error', 'secondary', 'contrast'];

const meta = {
    title: 'Feedback/Message',
    component: TumUiMessageComponent,
    args: {
        text: 'Your changes have been saved.',
        severity: 'info',
        icon: faCircleInfo,
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

export const Severities: Story = {
    render: () => ({
        props: { severities },
        template: `
            <div style="display: grid; width: min(32rem, 100%); gap: 0.75rem;">
                @for (severity of severities; track severity) {
                    <tum-ui-message [severity]="severity" [text]="severity + ' message'" />
                }
            </div>
        `,
    }),
    parameters: {
        controls: {
            disable: true,
        },
        layout: 'padded',
    },
};
