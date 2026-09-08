import { moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect } from 'storybook/test';

import { TumUiPanelComponent } from '../panel/tum-ui-panel.component';
import { TumUiProseComponent, TumUiProseDensity } from './tum-ui-prose.component';

interface ProseStoryArgs {
    density: TumUiProseDensity;
    html: string;
}

const ARTICLE = `
    <h2>Retention policy</h2>
    <p>A record is kept until its retention date. After it, the record stays readable for reference and no longer
    counts towards the reported totals.</p>
    <h3>Late arrivals</h3>
    <ul>
        <li>Within the grace period the record is stored and marked late.</li>
        <li>After the grace period the record is stored for reference only.</li>
    </ul>
    <p>Set the grace period with <code>retentionPolicy.gracePeriod</code>.</p>
`;

const EVERY_BLOCK = `
    <h1>Heading level one</h1>
    <p>An ordinary paragraph with <strong>strong emphasis</strong>, <em>emphasis</em>, an
    <a href="#prose">inline link</a> and an <code>inline code span</code>.</p>
    <h2>Heading level two</h2>
    <ul><li>Unordered item</li><li>Unordered item with a nested list<ul><li>Nested item</li></ul></li></ul>
    <h3>Heading level three</h3>
    <ol><li>Ordered item</li><li>Ordered item</li></ol>
    <h4>Heading level four</h4>
    <blockquote>A quotation, set off by a rule rather than by a box.</blockquote>
    <pre><code>export function total(values: number[]): number {
    return values.reduce((sum, value) => sum + value, 0);
}</code></pre>
    <table>
        <thead><tr><th>Stage</th><th>Duration</th></tr></thead>
        <tbody><tr><td>Analysis</td><td>2 min</td></tr><tr><td>Draft</td><td>6 min</td></tr></tbody>
    </table>
    <hr />
    <h5>Heading level five</h5>
    <h6>Heading level six</h6>
    <p>The last paragraph.</p>
`;

/**
 * Styles a subtree of author-generated HTML with the design system's typography.
 *
 * The component only styles: it injects no markup and sets no heading levels, so a projected document keeps the
 * outline its author gave it. Sanitising the HTML stays with the consumer — a design system must not own an
 * application's Markdown pipeline.
 */
const meta = {
    title: 'Data Display/Prose',
    component: TumUiProseComponent,
    parameters: {
        layout: 'padded',
    },
    args: {
        density: 'comfortable',
        html: ARTICLE,
    },
    argTypes: {
        density: {
            control: 'inline-radio',
            options: ['comfortable', 'compact'] satisfies TumUiProseDensity[],
        },
        html: {
            control: 'text',
        },
    },
    render: ({ density, html }) => ({
        props: { density, html },
        template: `<tum-ui-prose [density]="density" [innerHTML]="html"></tum-ui-prose>`,
    }),
} satisfies Meta<ProseStoryArgs>;

export default meta;

type Story = StoryObj<ProseStoryArgs>;

export const Default: Story = {};

/** Tightened block rhythm and a smaller body size, for prose that shares a card body with other content. */
export const Compact: Story = {
    args: {
        density: 'compact',
    },
};

/** Every block element the component styles, in one document. Read it in both themes. */
export const AllBlockElements: Story = {
    args: {
        html: EVERY_BLOCK,
    },
};

/**
 * Long-form reading is capped at `--tum-ui-prose-measure` (65ch by default) however wide its column is. Set the
 * property to `none` where the prose has to fill a column it shares with something else.
 */
export const Measure: Story = {
    args: {
        html: `<p>${'Prose that is allowed to run the full width of a wide screen costs the reader a line-return every time their eye travels back, which is why the measure is capped rather than fluid. '.repeat(4)}</p>`,
    },
    render: ({ density, html }) => ({
        props: { density, html },
        template: `
            <div class="tum-ui-story-stack">
                <tum-ui-prose [density]="density" [innerHTML]="html"></tum-ui-prose>
                <tum-ui-prose [density]="density" [innerHTML]="html" style="--tum-ui-prose-measure: none;"></tum-ui-prose>
            </div>
        `,
    }),
};

/** Inside a panel the prose is subordinate to the panel's own header, so `compact` is usually the right density. */
export const InsideAPanel: Story = {
    args: {
        density: 'compact',
    },
    decorators: [moduleMetadata({ imports: [TumUiPanelComponent, TumUiProseComponent] })],
    render: ({ density, html }) => ({
        props: { density, html },
        template: `
            <tum-ui-panel header="Retention policy" toggleable style="display: block; width: min(40rem, 100%);">
                <tum-ui-prose [density]="density" [innerHTML]="html"></tum-ui-prose>
            </tum-ui-panel>
        `,
    }),
};

/**
 * The regression this component exists to prevent, measured rather than argued.
 *
 * A host page's unlayered `h1`–`h6` rules — Bootstrap's, plus a `body, h1, h2, h3, h4 { font-weight: 400 }` reset —
 * beat Tailwind's `@layer utilities`, so a plain `text-lg font-semibold` on a heading silently renders at the host
 * page's size and weight. `.tum-ui-prose h2` is a class selector at specificity (0,1,1) in the same unlayered
 * origin and wins on specificity, with no `!important` anywhere. This story injects those exact host rules and
 * asserts the prose heading still renders at its own scale, while a bare heading beside it does not.
 */
export const CascadeRegression: Story = {
    tags: ['!dev', '!autodocs'],
    args: {
        html: '<h2>Prose heading</h2>',
    },
    render: ({ html }) => ({
        props: { html },
        template: `
            <div>
                <style>
                    h1, h2, h3, h4, h5, h6 { margin-top: 0; margin-bottom: 0.5rem; font-weight: 500; line-height: 1.2; }
                    h2 { font-size: calc(1.325rem + 0.9vw); }
                    body, h1, h2, h3, h4 { font-weight: 400; }
                </style>
                <h2 data-testid="bare-heading">Bare heading</h2>
                <tum-ui-prose [innerHTML]="html" data-testid="prose"></tum-ui-prose>
            </div>
        `,
    }),
    play: async ({ canvas }) => {
        const bare = getComputedStyle(canvas.getByTestId('bare-heading'));
        const scoped = getComputedStyle(canvas.getByTestId('prose').querySelector('h2')!);

        await expect(bare.fontWeight, 'the host page reset wins on a bare heading').toBe('400');
        await expect(scoped.fontWeight, 'the prose class selector wins on specificity').toBe('600');
        await expect(parseFloat(scoped.fontSize), 'the prose heading takes its size from the package scale').toBeLessThan(parseFloat(bare.fontSize));
        await expect(parseFloat(scoped.fontSize)).toBeCloseTo(18, 0);
    },
};

/**
 * Code sits on its own surface tier rather than borrowing the hover background, so a code span keeps body contrast
 * in both themes. Verified here rather than asserted in prose.
 */
export const CodeSurface: Story = {
    tags: ['!dev', '!autodocs'],
    args: {
        html: '<p>Configure it with <code>artemis.token</code>.</p><pre><code>artemis:\n    token: value</code></pre>',
    },
    render: ({ density, html }) => ({
        props: { density, html },
        template: `<tum-ui-prose [density]="density" [innerHTML]="html" data-testid="prose"></tum-ui-prose>`,
    }),
    play: async ({ canvas }) => {
        const prose = canvas.getByTestId('prose');
        const code = prose.querySelector('code')!;
        const pre = prose.querySelector('pre')!;
        const page = getComputedStyle(document.body).backgroundColor;

        await expect(getComputedStyle(code).backgroundColor, 'inline code sits on its own tier').not.toBe(page);
        await expect(getComputedStyle(pre).backgroundColor, 'a code block sits on its own tier').not.toBe(page);
        await expect(getComputedStyle(pre).fontFamily, 'code uses the monospace token').toContain('monospace');
    },
};
