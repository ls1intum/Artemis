import { moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect } from 'storybook/test';

import { TumUiProgressSpinnerComponent } from './tum-ui-progress-spinner.component';

const meta = {
    title: 'Feedback/Progress Spinner',
    component: TumUiProgressSpinnerComponent,
    decorators: [moduleMetadata({ imports: [TumUiProgressSpinnerComponent] })],
    args: {
        ariaLabel: 'Loading course data',
        size: 'large',
    },
    argTypes: {
        size: { control: 'inline-radio', options: ['small', 'medium', 'large'] },
    },
} satisfies Meta<TumUiProgressSpinnerComponent>;

export default meta;

type Story = StoryObj<TumUiProgressSpinnerComponent>;

export const Default: Story = {};

/**
 * Every diameter step. `small` sits on the baseline of the text it interrupts, `medium` beside a paragraph, `large`
 * fills a region that is waiting as a whole. Picking the step is how a spinner stays proportionate to what it is
 * reporting on — a page-sized spinner next to a button claims the whole page is busy when only the button is.
 */
export const Sizes: Story = {
    render: () => ({
        props: { sizes: ['small', 'medium', 'large'] },
        template: `
            <div style="display: flex; align-items: center; gap: 1.5rem;">
                @for (size of sizes; track size) {
                    <span style="display: inline-flex; align-items: center; gap: 0.5rem;">
                        <tum-ui-progress-spinner [size]="size" [ariaLabel]="'Loading ' + size" />
                        <span>{{ size }}</span>
                    </span>
                }
            </div>
        `,
    }),
};

/** A `small` spinner inline with the sentence it belongs to, which is the size step's reason for existing. */
export const InlineWithText: Story = {
    args: { size: 'small', ariaLabel: 'Checking the repository' },
    render: (args) => ({
        props: args,
        template: `
            <p style="display: flex; align-items: center; gap: 0.5rem; margin: 0;">
                <tum-ui-progress-spinner [size]="size" [ariaLabel]="ariaLabel" />
                <span>Checking the repository…</span>
            </p>
        `,
    }),
};

/**
 * Measured proof that the size is exposed as a state hook on the host and that the accessible name is the thing
 * being waited for. `role="status"` without a name is the one live region a page must not have.
 */
export const SizeIsOnTheHost: Story = {
    tags: ['!dev', '!autodocs'],
    args: { size: 'small', ariaLabel: 'Checking the repository' },
    play: async ({ canvasElement }) => {
        await expect(canvasElement.querySelector("tum-ui-progress-spinner[data-size='small']")).not.toBeNull();
        await expect(canvasElement.querySelector("tum-ui-progress-spinner[data-slot='progress-spinner']")).not.toBeNull();

        const host = canvasElement.querySelector('tum-ui-progress-spinner')!;
        await expect(host).toHaveAttribute('role', 'status');
        await expect(host).toHaveAttribute('aria-label', 'Checking the repository');
    },
};
