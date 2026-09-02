import { moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect } from 'storybook/test';

import { TumUiSeparatorComponent, TumUiSeparatorOrientation } from './tum-ui-separator.component';

/**
 * A rule between two regions or two rows.
 *
 * The reason it is a component rather than a border utility is the `decorative` decision: a rule that only groups
 * must stay out of the accessibility tree, and a rule that marks a real boundary must not.
 */
const meta = {
    title: 'Data Display/Separator',
    component: TumUiSeparatorComponent,
    parameters: {
        layout: 'padded',
    },
    args: {
        orientation: 'horizontal',
        decorative: true,
    },
    argTypes: {
        orientation: {
            control: 'inline-radio',
            options: ['horizontal', 'vertical'] satisfies TumUiSeparatorOrientation[],
        },
    },
    render: (args) => ({
        props: args,
        template: `
            <div style="display: block; width: min(24rem, 100%);">
                <p style="margin: 0 0 0.75rem;">Above the rule</p>
                <tum-ui-separator [orientation]="orientation" [decorative]="decorative" />
                <p style="margin: 0.75rem 0 0;">Below the rule</p>
            </div>
        `,
    }),
} satisfies Meta<TumUiSeparatorComponent>;

export default meta;

type Story = StoryObj<TumUiSeparatorComponent>;

export const Default: Story = {};

/** A boundary a screen-reader user needs to know about, for instance between two unlabelled regions. */
export const Semantic: Story = {
    args: {
        decorative: false,
    },
};

/** A vertical rule takes the height of the row it sits in; nothing has to be hard-coded to match its neighbours. */
export const Vertical: Story = {
    args: {
        orientation: 'vertical',
    },
    render: (args) => ({
        props: args,
        template: `
            <div style="display: flex; align-items: stretch; gap: 1rem;">
                <span>Draft</span>
                <tum-ui-separator [orientation]="orientation" [decorative]="decorative" />
                <span>Review</span>
                <tum-ui-separator [orientation]="orientation" [decorative]="decorative" />
                <span>Published</span>
            </div>
        `,
    }),
};

/** Between the rows of a grouped list, where a `role="separator"` per row would be pure noise. */
export const InAList: Story = {
    decorators: [moduleMetadata({ imports: [TumUiSeparatorComponent] })],
    render: () => ({
        props: {
            rows: ['Analysis', 'Draft', 'Review'],
        },
        template: `
            <ul role="list" aria-label="Stages" style="margin: 0; padding: 0; list-style: none; width: min(24rem, 100%);">
                @for (row of rows; track row; let last = $last) {
                    <li style="padding: 0.75rem 0;">{{ row }}</li>
                    @if (!last) { <tum-ui-separator /> }
                }
            </ul>
        `,
    }),
};

/**
 * The contract, measured: the decorative rule is absent from the accessibility tree and the semantic one is not.
 * jsdom does not compute roles, so this runs in a real browser.
 */
export const SemanticVersusDecorative: Story = {
    tags: ['!dev', '!autodocs'],
    decorators: [moduleMetadata({ imports: [TumUiSeparatorComponent] })],
    render: () => ({
        template: `
            <div class="tum-ui-story-stack">
                <tum-ui-separator data-testid="decorative" />
                <tum-ui-separator [decorative]="false" data-testid="semantic" />
                <tum-ui-separator [decorative]="false" orientation="vertical" data-testid="semantic-vertical" />
            </div>
        `,
    }),
    play: async ({ canvas }) => {
        await expect(canvas.getByTestId('decorative').getAttribute('role')).toBe('none');
        await expect(canvas.queryAllByRole('separator')).toHaveLength(2);
        await expect(canvas.getByTestId('semantic').getAttribute('aria-orientation'), 'horizontal is the role default').toBeNull();
        await expect(canvas.getByTestId('semantic-vertical').getAttribute('aria-orientation')).toBe('vertical');
    },
};
