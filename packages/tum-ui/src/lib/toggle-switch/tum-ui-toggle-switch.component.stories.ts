import { FormsModule } from '@angular/forms';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { fn } from 'storybook/test';

import { TumUiToggleSwitchComponent } from './tum-ui-toggle-switch.component';

interface ToggleSwitchStoryArgs {
    label: string;
    enabled: boolean;
    disabled: boolean;
    changed: (checked: boolean) => void;
}

const meta = {
    title: 'Forms/Toggle Switch',
    component: TumUiToggleSwitchComponent,
    args: {
        label: 'Email notifications',
        enabled: false,
        disabled: false,
        changed: fn(),
    },
    render: (args) => ({
        props: args,
        moduleMetadata: {
            imports: [FormsModule],
        },
        template: `
            <span id="notification-label">{{ label }}</span>
            <tum-ui-toggle-switch
                inputId="notifications"
                aria-labelledby="notification-label"
                [(ngModel)]="enabled"
                [ngModelOptions]="{ standalone: true }"
                [disabled]="disabled"
                (changed)="changed($event)"
            />
        `,
    }),
} satisfies Meta<ToggleSwitchStoryArgs>;

export default meta;

type Story = StoryObj<ToggleSwitchStoryArgs>;

export const Default: Story = {};

export const On: Story = {
    args: {
        enabled: true,
    },
};

export const Disabled: Story = {
    args: {
        enabled: true,
        disabled: true,
    },
};
