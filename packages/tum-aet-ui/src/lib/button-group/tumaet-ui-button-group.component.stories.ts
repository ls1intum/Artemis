import { moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';

import { expect } from 'storybook/test';

import { TumAetUiButtonDirective } from '../button/tumaet-ui-button.directive';
import { TumAetUiButtonComponent } from '../button/tumaet-ui-button.component';
import { TumAetUiButtonGroupComponent } from './tumaet-ui-button-group.component';

interface ButtonGroupStoryArgs {
    firstLabel: string;
    secondLabel: string;
    thirdLabel: string;
}

const meta = {
    title: 'Actions/Button Group',
    component: TumAetUiButtonGroupComponent,
    subcomponents: {
        Button: TumAetUiButtonComponent,
    },
    decorators: [
        moduleMetadata({
            imports: [TumAetUiButtonComponent, TumAetUiButtonDirective],
        }),
    ],
    args: {
        firstLabel: 'Previous',
        secondLabel: 'Today',
        thirdLabel: 'Next',
    },
    render: (args) => ({
        props: args,
        template: `
            <tumaet-ui-button-group aria-label="Date navigation">
                <tumaet-ui-button severity="secondary">{{ firstLabel }}</tumaet-ui-button>
                <tumaet-ui-button severity="secondary">{{ secondLabel }}</tumaet-ui-button>
                <tumaet-ui-button severity="secondary">{{ thirdLabel }}</tumaet-ui-button>
            </tumaet-ui-button-group>
        `,
    }),
} satisfies Meta<ButtonGroupStoryArgs>;

export default meta;

type Story = StoryObj<ButtonGroupStoryArgs>;

export const Default: Story = {
    play: async ({ canvas }) => {
        const [first, middle, last] = canvas.getAllByRole('button').map((button) => getComputedStyle(button));
        await expect(first.borderStartStartRadius).not.toBe('0px');
        await expect(first.borderStartEndRadius).toBe('0px');
        await expect(first.borderEndEndRadius).toBe('0px');
        await expect(first.borderInlineEndWidth).toBe('0px');
        await expect(middle.borderRadius).toBe('0px');
        await expect(middle.borderInlineEndWidth).toBe('0px');
        await expect(last.borderStartStartRadius).toBe('0px');
        await expect(last.borderEndStartRadius).toBe('0px');
        await expect(last.borderStartEndRadius).not.toBe('0px');
        await expect(last.borderInlineEndWidth).not.toBe('0px');
    },
};

export const NativeButtons: Story = {
    render: (args) => ({
        props: args,
        template: `
            <tumaet-ui-button-group aria-label="Date navigation">
                <button tumAetUiButton severity="secondary">{{ firstLabel }}</button>
                <button tumAetUiButton severity="secondary">{{ secondLabel }}</button>
                <button tumAetUiButton severity="secondary">{{ thirdLabel }}</button>
            </tumaet-ui-button-group>
        `,
    }),
    play: Default.play,
};

export const NativeButtonsRtl: Story = {
    render: (args) => ({
        props: args,
        template: `
            <tumaet-ui-button-group dir="rtl" aria-label="Date navigation">
                <button tumAetUiButton severity="secondary">{{ firstLabel }}</button>
                <button tumAetUiButton severity="secondary">{{ secondLabel }}</button>
                <button tumAetUiButton severity="secondary">{{ thirdLabel }}</button>
            </tumaet-ui-button-group>
        `,
    }),
    play: Default.play,
};
