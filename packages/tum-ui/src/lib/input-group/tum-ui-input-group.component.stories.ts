import { moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';

import { TumUiInputDirective } from '../input/tum-ui-input.directive';
import { TumUiInputGroupAddonComponent } from './tum-ui-input-group-addon.component';
import { TumUiInputGroupComponent } from './tum-ui-input-group.component';

interface InputGroupStoryArgs {
    label: string;
    placeholder: string;
    prefix: string;
    suffix: string;
}

const meta = {
    title: 'Forms/Input Group',
    component: TumUiInputGroupComponent,
    subcomponents: {
        Addon: TumUiInputGroupAddonComponent,
        Input: TumUiInputDirective,
    },
    args: {
        label: 'Budget',
        placeholder: '0',
        prefix: '€',
        suffix: 'EUR',
    },
    decorators: [
        moduleMetadata({
            imports: [TumUiInputDirective, TumUiInputGroupAddonComponent],
        }),
    ],
    render: (args) => ({
        props: args,
        template: `
            <label for="budget">{{ label }}</label>
            <tum-ui-input-group>
                <tum-ui-input-group-addon>{{ prefix }}</tum-ui-input-group-addon>
                <input id="budget" tumUiInput inputmode="decimal" [placeholder]="placeholder" />
                <tum-ui-input-group-addon>{{ suffix }}</tum-ui-input-group-addon>
            </tum-ui-input-group>
        `,
    }),
} satisfies Meta<InputGroupStoryArgs>;

export default meta;

type Story = StoryObj<InputGroupStoryArgs>;

export const Default: Story = {};
