import { faCircleInfo } from '@fortawesome/free-solid-svg-icons';
import type { Meta, StoryObj } from '@storybook/angular-vite';

import { TumUiMessageComponent, TumUiMessageSeverity } from './tum-ui-message.component';

const severities: TumUiMessageSeverity[] = ['info', 'success', 'warn', 'error', 'secondary', 'contrast'];
const examples: { severity: TumUiMessageSeverity; text: string }[] = [
    { severity: 'info', text: 'The exercise opens tomorrow.' },
    { severity: 'success', text: 'Your changes have been saved.' },
    { severity: 'warn', text: 'The submission deadline is approaching.' },
    { severity: 'error', text: 'The submission could not be uploaded.' },
    { severity: 'secondary', text: 'No assessment is available yet.' },
    { severity: 'contrast', text: 'Maintenance starts at 22:00.' },
];

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
        props: { examples },
        template: `
            <div style="display: grid; width: min(32rem, 100%); gap: 0.75rem;">
                @for (example of examples; track example.severity) {
                    <tum-ui-message [severity]="example.severity" [text]="example.text" />
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
