import { moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect } from 'storybook/test';

import { TumUiCardComponent } from '../card/tum-ui-card.component';
import { TumUiSkeletonComponent } from './tum-ui-skeleton.component';

const meta = {
    title: 'Feedback/Skeleton',
    component: TumUiSkeletonComponent,
    decorators: [moduleMetadata({ imports: [TumUiSkeletonComponent, TumUiCardComponent] })],
    args: {
        lines: 1,
    },
    parameters: {
        layout: 'padded',
    },
} satisfies Meta<TumUiSkeletonComponent>;

export default meta;

type Story = StoryObj<TumUiSkeletonComponent>;

/** One line. It fills its container, so the box that reserves the space is the consumer's to size. */
export const Line: Story = {
    render: (args) => ({
        props: args,
        template: `<div style="width: 24rem;"><tum-ui-skeleton [lines]="lines" /></div>`,
    }),
};

/** A paragraph. The last line stops short, which is what keeps a stack of bars from reading as a table. */
export const Paragraph: Story = {
    args: { lines: 3 },
    render: (args) => ({
        props: args,
        template: `<div style="width: 24rem;"><tum-ui-skeleton [lines]="lines" /></div>`,
    }),
};

/** A block: an exact box for something with a known shape, such as a thumbnail or a chart. */
export const Block: Story = {
    render: () => ({
        template: `<tum-ui-skeleton width="12rem" height="8rem" />`,
    }),
};

/**
 * The a11y contract, which is the part most kits get wrong. The placeholder is `aria-hidden`; the **container**
 * carries `aria-busy` and an sr-only word naming what is loading. A shimmer is not a status, and neither is a bar.
 */
export const AnnouncedByItsContainer: Story = {
    render: () => ({
        template: `
            <div aria-busy="true" style="width: 24rem;">
                <span class="tum:sr-only">Loading changed files</span>
                <tum-ui-skeleton lines="3" />
            </div>
        `,
    }),
};

/**
 * In a card, filling the box the arriving content will occupy. Reserve the real height here — a placeholder that is
 * shorter than its content moves the page under the reader the moment the data lands.
 */
export const InACard: Story = {
    render: () => ({
        template: `
            <tum-ui-card variant="outline" style="display: block; width: 26rem;">
                <div aria-busy="true">
                    <span class="tum:sr-only">Loading the run summary</span>
                    <tum-ui-skeleton width="9rem" height="1.25rem" />
                    <div style="height: 1rem;"></div>
                    <tum-ui-skeleton lines="3" />
                </div>
            </tum-ui-card>
        `,
    }),
};

/**
 * The exchange the component does own: placeholder and content share one grid cell, so the real thing fades in
 * exactly where the placeholder was instead of the page resizing around it. Nothing moves; only opacity changes.
 */
export const CrossfadeToContent: Story = {
    render: () => ({
        props: { loaded: false },
        template: `
            <div style="display: grid; width: 24rem;">
                <div style="grid-area: 1 / 1; transition: opacity var(--tumaet-ui-duration-state) var(--tumaet-ui-easing-standard);" [style.opacity]="loaded ? 0 : 1">
                    <tum-ui-skeleton lines="3" />
                </div>
                <p style="grid-area: 1 / 1; margin: 0; transition: opacity var(--tumaet-ui-duration-state) var(--tumaet-ui-easing-standard);" [style.opacity]="loaded ? 1 : 0">
                    The agent wrote three files into the solution repository and one into the template repository.
                </p>
            </div>
            <button type="button" style="margin-top: 1rem;" (click)="loaded = !loaded">Toggle loaded</button>
        `,
    }),
};

/**
 * Measured proof of the decision the component is named for: there is no shimmer. An infinite auto-starting
 * animation running past five seconds alongside other content is WCAG 2.2.2 territory, and it says nothing a still
 * block does not — so the assertion is that the computed animation is `none`, in a real browser.
 */
export const NoShimmer: Story = {
    tags: ['!dev', '!autodocs'],
    args: { lines: 3 },
    render: (args) => ({
        props: args,
        template: `<div style="width: 24rem;"><tum-ui-skeleton [lines]="lines" /></div>`,
    }),
    play: async ({ canvasElement }) => {
        const skeleton = canvasElement.querySelector('tum-ui-skeleton')!;
        const lines = [...skeleton.querySelectorAll('.tum-ui-skeleton-line')];
        await expect(lines).toHaveLength(3);
        await expect(skeleton.getAttribute('aria-hidden')).toBe('true');
        for (const line of lines) {
            await expect(getComputedStyle(line).animationName, 'a placeholder does not animate').toBe('none');
        }
        const [first] = lines;
        const last = lines[lines.length - 1];
        await expect(last.getBoundingClientRect().width, 'the last line stops short').toBeLessThan(first.getBoundingClientRect().width);
    },
};
