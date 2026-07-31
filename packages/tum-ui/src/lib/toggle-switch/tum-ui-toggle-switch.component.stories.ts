import { FormsModule } from '@angular/forms';
import { moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { fn } from 'storybook/test';

import { inlineControlStoryDecorator } from '../../../.storybook/story-decorators';
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
    argTypes: {
        changed: { control: false },
    },
    decorators: [
        inlineControlStoryDecorator,
        moduleMetadata({
            imports: [FormsModule],
        }),
    ],
    render: (args) => {
        return {
            props: { ...args },
            template: `
                <span id="notification-label">{{ label }}</span>
                <tum-ui-toggle-switch
                    inputId="notifications"
                    aria-labelledby="notification-label"
                    [(ngModel)]="enabled"
                    [disabled]="disabled"
                    (changed)="changed($event)"
                />
            `,
        };
    },
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
