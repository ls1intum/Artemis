import { argsToTemplate } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect, fn } from 'storybook/test';

import { inlineControlStoryDecorator } from '../../../.storybook/story-decorators';
import { TumUiCheckboxChangeEvent, TumUiCheckboxComponent } from './tum-ui-checkbox.component';

interface CheckboxStoryArgs {
    label: string;
    checked: boolean;
    disabled: boolean;
    changed: (event: TumUiCheckboxChangeEvent) => void;
}

const meta = {
    title: 'Forms/Checkbox',
    component: TumUiCheckboxComponent,
    args: {
        label: 'Accept the terms and conditions',
        checked: false,
        disabled: false,
        changed: fn(),
    },
    argTypes: {
        changed: { control: false },
    },
    decorators: [inlineControlStoryDecorator],
    render: (args) => {
        return {
            props: { ...args },
            template: `
                <label for="terms">
                    <tum-ui-checkbox
                        inputId="terms"
                        name="terms"
                        [checked]="checked"
                        ${argsToTemplate(args, { exclude: ['checked', 'label', 'changed'] })}
                        (changed)="checked = $event.checked; changed($event)"
                    />
                    {{ label }}
                </label>
            `,
        };
    },
} satisfies Meta<CheckboxStoryArgs>;

export default meta;

type Story = StoryObj<CheckboxStoryArgs>;

export const Default: Story = {};

export const Checked: Story = {
    args: {
        checked: true,
    },
};

export const Disabled: Story = {
    args: {
        checked: true,
        disabled: true,
    },
};

/**
 * A checkbox stays square beside a label that wraps onto several lines.
 * <p>
 * Two things used to break this, and both showed only once the label needed more than one line, because that is when
 * the row is taller than the control. The size is derived from `--tumaet-ui-spacing`, and in a context that does not
 * carry that token the calc() was invalid: the width collapsed and the height fell back to auto, so a flex row's
 * default `stretch` grew the control to the full height of the label. Separately, the visible box sized itself as 100%
 * of the host and added a border on top, so without a global border-box reset it came out two pixels taller than wide.
 * <p>
 * The box is measured rather than the host, because the box is what a reader actually sees.
 */
export const StaysSquareBesideAWrappingLabel: Story = {
    tags: ['!dev', '!autodocs'],
    render: (args) => ({
        props: { ...args },
        template: `
            <div style="width: 420px;">
                <div style="display: flex; align-items: center; gap: 8px;">
                    <tum-ui-checkbox inputId="wrap-centered" name="wrap-centered" data-testid="wrap-centered" />
                    <label for="wrap-centered">Alle Passkeys löschen. Du musst deine Geräte erneut registrieren, bevor du dich das nächste Mal mit einem Passkey anmeldest.</label>
                </div>
                <div style="display: flex; gap: 8px;">
                    <tum-ui-checkbox inputId="wrap-stretched" name="wrap-stretched" data-testid="wrap-stretched" />
                    <label for="wrap-stretched">Alle Passkeys löschen. Du musst deine Geräte erneut registrieren, bevor du dich das nächste Mal mit einem Passkey anmeldest.</label>
                </div>
                <div style="--tumaet-ui-spacing: initial; display: flex; gap: 8px;">
                    <tum-ui-checkbox inputId="wrap-untokened" name="wrap-untokened" data-testid="wrap-untokened" />
                    <label for="wrap-untokened">Alle Passkeys löschen. Du musst deine Geräte erneut registrieren, bevor du dich das nächste Mal mit einem Passkey anmeldest.</label>
                </div>
            </div>
        `,
    }),
    play: async ({ canvas }) => {
        for (const id of ['wrap-centered', 'wrap-stretched', 'wrap-untokened']) {
            const box = canvas.getByTestId(id).querySelector('.tum-ui-checkbox-box')!.getBoundingClientRect();

            await expect(box.width, `${id} width`).toBeGreaterThan(0);
            await expect(box.width, `${id} is square`).toBeCloseTo(box.height, 1);
        }
    },
};

export const StaysSquareInATightRow: Story = {
    tags: ['!dev', '!autodocs'],
    render: (args) => ({
        props: { ...args },
        template: `
            <div style="display: flex; align-items: center; gap: 8px; width: 120px;">
                <tum-ui-checkbox inputId="tight" name="tight" data-testid="tight-checkbox" />
                <label for="tight">A label far too long to fit beside the box in this row</label>
            </div>
        `,
    }),
    play: async ({ canvas }) => {
        const checkbox = canvas.getByTestId('tight-checkbox');
        const { width, height } = checkbox.getBoundingClientRect();

        await expect(width).toBeGreaterThan(0);
        await expect(width).toBeCloseTo(height, 1);
    },
};
