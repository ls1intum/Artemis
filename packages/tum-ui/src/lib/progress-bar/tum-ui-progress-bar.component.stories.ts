import { moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect } from 'storybook/test';

import { TumUiProgressBarComponent } from './tum-ui-progress-bar.component';

const meta = {
    title: 'Feedback/Progress Bar',
    component: TumUiProgressBarComponent,
    args: {
        ariaLabel: 'Course completion',
        severity: 'primary',
        value: 60,
        min: 0,
        max: 100,
        valueText: '60%',
        showValue: true,
    },
    argTypes: {
        severity: {
            control: 'select',
            options: ['primary', 'success', 'warning', 'danger', 'info'],
        },
        value: {
            control: {
                type: 'range',
                min: 0,
                max: 100,
                step: 1,
            },
        },
    },
    parameters: {
        layout: 'padded',
    },
} satisfies Meta<TumUiProgressBarComponent>;

export default meta;

type Story = StoryObj<TumUiProgressBarComponent>;

export const Default: Story = {};

/**
 * Nothing done yet. The reading stays visible at zero, which it could not do while the label lived inside the fill —
 * the old `Empty` story had to switch the label off to hide that.
 */
export const Empty: Story = {
    args: {
        value: 0,
        valueText: '0%',
    },
};

export const Complete: Story = {
    args: {
        value: 100,
        valueText: '100%',
    },
};

/** Colour is never the only signal that a threshold was crossed: the reading says so in words. */
export const NearTheLimit: Story = {
    args: {
        severity: 'warning',
        value: 88,
        valueText: '88% — near budget limit',
        ariaLabel: 'Budget used',
    },
};

export const OverTheLimit: Story = {
    args: {
        severity: 'danger',
        value: 100,
        valueText: 'Over budget',
        ariaLabel: 'Budget used',
    },
};

/**
 * A count, not a percentage. Without `min` and `max` this bar would announce itself as "40 percent" — a number
 * that appears nowhere on the surface — and `valueText` is what makes the announcement match the printed words.
 */
export const CountedScale: Story = {
    args: {
        min: 0,
        max: 42,
        value: 17,
        valueText: '17 of 42 files',
        ariaLabel: 'Files written',
    },
};

/** A scale that does not start at zero. The fill reports position within the range, not the raw value. */
export const OffsetScale: Story = {
    args: {
        min: 10,
        max: 20,
        value: 15,
        valueText: '15 of 10–20',
        ariaLabel: 'Score',
    },
};

/** Projected content replaces the reading entirely, for a label the consumer wants to compose. */
export const ProjectedLabel: Story = {
    args: {
        value: 30,
        showValue: false,
    },
    decorators: [moduleMetadata({ imports: [TumUiProgressBarComponent] })],
    render: (args) => ({
        props: args,
        template: `
            <tum-ui-progress-bar [value]="value" [ariaLabel]="ariaLabel" [severity]="severity" [showValue]="false">
                <strong>Step 3</strong>&nbsp;of 10
            </tum-ui-progress-bar>
        `,
    }),
};

/** Every severity in one column, so a reviewer can confirm the fills stay distinguishable in both themes. */
export const AllSeverities: Story = {
    parameters: {
        layout: 'padded',
    },
    decorators: [moduleMetadata({ imports: [TumUiProgressBarComponent] })],
    render: () => ({
        props: {
            rows: [
                { severity: 'primary', value: 60 },
                { severity: 'info', value: 60 },
                { severity: 'success', value: 60 },
                { severity: 'warning', value: 60 },
                { severity: 'danger', value: 60 },
            ],
        },
        template: `
            <div style="display: grid; gap: 1rem;">
                @for (row of rows; track row.severity) {
                    <tum-ui-progress-bar [severity]="row.severity" [value]="row.value" [valueText]="row.severity" [ariaLabel]="row.severity" />
                }
            </div>
        `,
    }),
};

/**
 * Measured proof of the two contracts jsdom cannot see: the reading is a sibling of the fill rather than a child of
 * it, so it survives at any value; and the fill's transition is withheld until the first value has been painted.
 */
export const LabelAndFirstPaint: Story = {
    tags: ['!dev', '!autodocs'],
    args: {
        value: 4,
        max: 100,
        valueText: '4 of 100 files',
    },
    play: async ({ canvasElement }) => {
        const bar = canvasElement.querySelector('tum-ui-progress-bar')!;
        const fill = bar.querySelector('.tum-ui-progress-bar-value')!;
        const label = bar.querySelector('.tum-ui-progress-bar-label')!;

        await expect(fill.contains(label), 'the reading is not inside the clipping fill').toBe(false);
        await expect(label.textContent?.trim()).toBe('4 of 100 files');
        await expect(label.getBoundingClientRect().width, 'the reading is legible at 4%').toBeGreaterThan(0);
        await expect(bar.getAttribute('data-committed'), 'the first value has been committed').toBe('true');
    },
};
