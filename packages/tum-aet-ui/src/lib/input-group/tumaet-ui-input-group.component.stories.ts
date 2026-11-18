import { moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';

import { formStoryDecorator } from '../../../.storybook/story-decorators';
import { TumAetUiInputDirective } from '../input/tumaet-ui-input.directive';
import { TumAetUiInputGroupAddonComponent } from './tumaet-ui-input-group-addon.component';
import { TumAetUiInputGroupComponent } from './tumaet-ui-input-group.component';

interface InputGroupStoryArgs {
    label: string;
    placeholder: string;
    prefix: string;
    suffix: string;
}

const meta = {
    title: 'Forms/Input Group',
    component: TumAetUiInputGroupComponent,
    subcomponents: {
        Addon: TumAetUiInputGroupAddonComponent,
        Input: TumAetUiInputDirective,
    },
    args: {
        label: 'Budget',
        placeholder: '0',
        prefix: '€',
        suffix: 'EUR',
    },
    decorators: [
        formStoryDecorator,
        moduleMetadata({
            imports: [TumAetUiInputDirective, TumAetUiInputGroupAddonComponent],
        }),
    ],
    render: (args) => ({
        props: args,
        template: `
            <label for="budget">{{ label }}</label>
            <tumaet-ui-input-group>
                <tumaet-ui-input-group-addon>{{ prefix }}</tumaet-ui-input-group-addon>
                <input id="budget" tumAetUiInput inputmode="decimal" [placeholder]="placeholder" />
                <tumaet-ui-input-group-addon>{{ suffix }}</tumaet-ui-input-group-addon>
            </tumaet-ui-input-group>
        `,
    }),
} satisfies Meta<InputGroupStoryArgs>;

export default meta;

type Story = StoryObj<InputGroupStoryArgs>;

export const Default: Story = {};
