import { moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect } from 'storybook/test';

import { TumUiButtonComponent } from '../button/tum-ui-button.component';
import { TumUiTagComponent, TumUiTagSeverity } from './tum-ui-tag.component';

const severities: TumUiTagSeverity[] = ['secondary', 'success', 'info', 'warning', 'danger', 'contrast'];
const meta = {
    title: 'Data Display/Tag',
    component: TumUiTagComponent,
    decorators: [moduleMetadata({ imports: [TumUiTagComponent, TumUiButtonComponent] })],
    args: {
        value: 'Draft',
        severity: 'secondary',
        size: 'medium',
        variant: 'solid',
        rounded: false,
    },
    argTypes: {
        severity: {
            control: 'select',
            options: severities,
        },
        size: { control: 'inline-radio', options: ['small', 'medium', 'large'] },
        variant: { control: 'inline-radio', options: ['solid', 'quiet'] },
    },
} satisfies Meta<TumUiTagComponent>;

export default meta;

type Story = StoryObj<TumUiTagComponent>;

export const Default: Story = {};

export const Published: Story = {
    args: {
        severity: 'success',
        value: 'Published',
    },
};

export const InReview: Story = {
    args: {
        severity: 'info',
        value: 'In review',
    },
};

export const DueSoon: Story = {
    args: {
        severity: 'warning',
        value: 'Due soon',
    },
};

export const Overdue: Story = {
    args: {
        severity: 'danger',
        value: 'Overdue',
    },
};

export const Archived: Story = {
    args: {
        severity: 'contrast',
        value: 'Archived',
    },
};

/** Every size, so a tag can be matched to the control it sits beside instead of always overpowering it. */
export const Sizes: Story = {
    render: () => ({
        props: { sizes: ['small', 'medium', 'large'] },
        template: `
            <div style="display: flex; align-items: center; gap: 0.75rem;">
                @for (size of sizes; track size) {
                    <tum-ui-tag [size]="size" severity="info" [value]="size" />
                }
            </div>
        `,
    }),
};

/**
 * Solid against quiet, at the same severity. A badge as loud as the label it annotates competes with the thing it
 * describes rather than describing it; `quiet` keeps the colour and drops the weight.
 */
export const Quiet: Story = {
    render: () => ({
        props: { severities },
        template: `
            <div style="display: grid; grid-template-columns: auto auto; gap: 0.5rem 0.75rem; align-items: center;">
                @for (severity of severities; track severity) {
                    <tum-ui-tag [severity]="severity" [value]="severity" />
                    <tum-ui-tag [severity]="severity" variant="quiet" [value]="severity" />
                }
            </div>
        `,
    }),
};

/** A tag sized to the button beside it, which is what the size step exists for. */
export const NextToAControl: Story = {
    render: () => ({
        template: `
            <div style="display: flex; align-items: center; gap: 0.5rem;">
                <tum-ui-button size="small" variant="outlined">Regenerate</tum-ui-button>
                <tum-ui-tag size="small" variant="quiet" severity="info" value="Draft" />
            </div>
        `,
    }),
};

/** A tag with an icon beside its text: `value` and projected content render together, neither suppressing the other. */
export const ValueAndProjection: Story = {
    render: () => ({
        template: `<tum-ui-tag severity="success" value="Passed">&nbsp;<span aria-hidden="true">✓</span></tum-ui-tag>`,
    }),
};

/**
 * Measured proof that the state hook is on the host. `tum-ui-tag[data-severity='danger']` has to match from
 * outside; while the attribute sat on the inner span it did not, and an application could not select on it at all.
 */
export const StateHookOnTheHost: Story = {
    tags: ['!dev', '!autodocs'],
    args: { severity: 'danger', size: 'small', variant: 'quiet', value: 'Overdue' },
    play: async ({ canvasElement }) => {
        await expect(canvasElement.querySelector("tum-ui-tag[data-severity='danger']")).not.toBeNull();
        await expect(canvasElement.querySelector("tum-ui-tag[data-size='small']")).not.toBeNull();
        await expect(canvasElement.querySelector("tum-ui-tag[data-variant='quiet']")).not.toBeNull();
        await expect(canvasElement.querySelector("tum-ui-tag[data-slot='tag']")).not.toBeNull();
    },
};
