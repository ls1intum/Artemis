import { moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect } from 'storybook/test';

import { TumUiButtonComponent } from '../button/tum-ui-button.component';
import { TumUiTagComponent } from '../tag/tum-ui-tag.component';
import {
    TumUiCardActionComponent,
    TumUiCardContentComponent,
    TumUiCardDescriptionComponent,
    TumUiCardFooterComponent,
    TumUiCardHeaderComponent,
    TumUiCardTitleComponent,
} from './tum-ui-card-parts.component';
import { TumUiCardComponent } from './tum-ui-card.component';

const imports = [
    TumUiCardComponent,
    TumUiCardHeaderComponent,
    TumUiCardTitleComponent,
    TumUiCardDescriptionComponent,
    TumUiCardActionComponent,
    TumUiCardContentComponent,
    TumUiCardFooterComponent,
    TumUiButtonComponent,
    TumUiTagComponent,
];

const meta = {
    title: 'Data Display/Card',
    component: TumUiCardComponent,
    decorators: [moduleMetadata({ imports })],
    args: {
        size: 'medium',
        variant: 'elevated',
    },
    argTypes: {
        size: { control: 'inline-radio', options: ['small', 'medium', 'large'] },
        variant: { control: 'inline-radio', options: ['elevated', 'outline', 'flat'] },
        header: { control: false },
        subheader: { control: false },
    },
    parameters: {
        layout: 'padded',
    },
} satisfies Meta<TumUiCardComponent>;

export default meta;

type Story = StoryObj<TumUiCardComponent>;

/** The default composition: a real heading, a description, a body. */
export const Default: Story = {
    render: (args) => ({
        props: args,
        template: `
            <tum-ui-card [size]="size" [variant]="variant" style="display: block; width: 26rem;">
                <tum-ui-card-header>
                    <tum-ui-card-title [level]="2">Workspace progress</tum-ui-card-title>
                    <tum-ui-card-description>Data platform, current milestone</tum-ui-card-description>
                </tum-ui-card-header>
                <tum-ui-card-content>You finished 8 of 12 tasks.</tum-ui-card-content>
            </tum-ui-card>
        `,
    }),
};

/**
 * Title left, control right — the most common card header there is, and the one that could not be built at all
 * before the action slot existed without abandoning the component and hand-rolling the header.
 */
export const HeaderWithAction: Story = {
    render: (args) => ({
        props: args,
        template: `
            <tum-ui-card [size]="size" [variant]="variant" style="display: block; width: 26rem;">
                <tum-ui-card-header>
                    <tum-ui-card-title [level]="2">Progress</tum-ui-card-title>
                    <tum-ui-card-description>Five stages, in order.</tum-ui-card-description>
                    <tum-ui-card-action><tum-ui-tag size="small" variant="quiet" value="Step 2 of 5" /></tum-ui-card-action>
                </tum-ui-card-header>
                <tum-ui-card-content>Writing the summary document.</tum-ui-card-content>
            </tum-ui-card>
        `,
    }),
};

/** No header at all: a card that is only a body. */
export const NoHeader: Story = {
    render: (args) => ({
        props: args,
        template: `
            <tum-ui-card [size]="size" [variant]="variant" style="display: block; width: 26rem;">
                <tum-ui-card-content>A card can be nothing but its content.</tum-ui-card-content>
            </tum-ui-card>
        `,
    }),
};

/** Header, content and footer, all inside the same padded body — one rhythm, one code path. */
export const WithFooter: Story = {
    render: (args) => ({
        props: args,
        template: `
            <tum-ui-card [size]="size" [variant]="variant" style="display: block; width: 26rem;">
                <tum-ui-card-header>
                    <tum-ui-card-title [level]="2">Nightly data import</tum-ui-card-title>
                    <tum-ui-card-action><tum-ui-button size="small" variant="text">Cancel</tum-ui-button></tum-ui-card-action>
                </tum-ui-card-header>
                <tum-ui-card-content>Four of five stages finished.</tum-ui-card-content>
                <tum-ui-card-footer>Updated a few seconds ago</tum-ui-card-footer>
            </tum-ui-card>
        `,
    }),
};

/** Every spacing step, so the difference is comparable rather than described. */
export const Sizes: Story = {
    render: () => ({
        props: { sizes: ['small', 'medium', 'large'] },
        template: `
            <div style="display: grid; gap: 1rem; width: 26rem;">
                @for (size of sizes; track size) {
                    <tum-ui-card [size]="size" variant="outline">
                        <tum-ui-card-header><tum-ui-card-title [level]="3">{{ size }}</tum-ui-card-title></tum-ui-card-header>
                        <tum-ui-card-content>Body text at the {{ size }} spacing step.</tum-ui-card-content>
                    </tum-ui-card>
                }
            </div>
        `,
    }),
};

/**
 * Every surface treatment. `outline` is the one to reach for when several cards sit side by side: four shadows read
 * as four equal claims and destroy the page's hierarchy.
 */
export const Variants: Story = {
    render: () => ({
        props: { variants: ['elevated', 'outline', 'flat'] },
        template: `
            <div style="display: grid; gap: 1rem; width: 26rem;">
                @for (variant of variants; track variant) {
                    <tum-ui-card [variant]="variant">
                        <tum-ui-card-header><tum-ui-card-title [level]="3">{{ variant }}</tum-ui-card-title></tum-ui-card-header>
                        <tum-ui-card-content>Body text on the {{ variant }} surface.</tum-ui-card-content>
                    </tum-ui-card>
                }
            </div>
        `,
    }),
};

/**
 * The heading levels, rendered. A card is a section, and a section whose title is a `<div>` is invisible to heading
 * navigation — which was true of every card in the application before `level` existed.
 */
export const TitleLevels: Story = {
    render: () => ({
        props: { levels: [2, 3, 4] },
        template: `
            <div style="display: grid; gap: 1rem; width: 26rem;">
                @for (level of levels; track level) {
                    <tum-ui-card variant="outline">
                        <tum-ui-card-header>
                            <tum-ui-card-title [level]="level">Heading level {{ level }}</tum-ui-card-title>
                            <tum-ui-card-description>role="heading" with aria-level="{{ level }}"</tum-ui-card-description>
                        </tum-ui-card-header>
                    </tum-ui-card>
                }
                <tum-ui-card variant="outline">
                    <tum-ui-card-header>
                        <tum-ui-card-title>No level</tum-ui-card-title>
                        <tum-ui-card-description>Not a section: plain emphasised text, outside the outline.</tum-ui-card-description>
                    </tum-ui-card-header>
                </tum-ui-card>
            </div>
        `,
    }),
};

/** Custom internal rhythm through the component-scoped custom property, which is the sanctioned override route. */
export const CustomSpacing: Story = {
    render: () => ({
        template: `
            <tum-ui-card variant="outline" style="display: block; width: 26rem; --tum-ui-card-spacing: 2.5rem;">
                <tum-ui-card-header><tum-ui-card-title [level]="3">Roomier</tum-ui-card-title></tum-ui-card-header>
                <tum-ui-card-content>Set --tum-ui-card-spacing on the host; a class override would lose to stylesheet order.</tum-ui-card-content>
            </tum-ui-card>
        `,
    }),
};

/**
 * The deprecated string API, kept working so no existing call site breaks in this change. It is here to be seen
 * next to the composition above: the title is a `<div>`, there is nowhere to put an action, and
 * `[tumUiCardHeader]` lands outside the padded body while `[tumUiCardFooter]` lands inside it.
 */
export const DeprecatedStringApi: Story = {
    args: {
        header: 'Total tokens',
        subheader: 'last 30 days',
    },
    render: (args) => ({
        props: args,
        template: `
            <tum-ui-card [header]="header" [subheader]="subheader" style="display: block; width: 26rem;">
                <p style="margin: 0;">724,318 billable tokens.</p>
                <small tumUiCardFooter style="color: var(--tumaet-ui-muted-color);">Updated a few seconds ago</small>
            </tum-ui-card>
        `,
    }),
};

/** Measured proof that the title reaches the heading outline, and that the header keeps its action beside it. */
export const HeadingContract: Story = {
    tags: ['!dev', '!autodocs'],
    render: () => ({
        template: `
            <tum-ui-card variant="outline" style="display: block; width: 26rem;">
                <tum-ui-card-header>
                    <tum-ui-card-title [level]="2">Progress</tum-ui-card-title>
                    <tum-ui-card-description>Five stages, in order.</tum-ui-card-description>
                    <tum-ui-card-action><tum-ui-tag size="small" variant="quiet" value="Step 2 of 5" /></tum-ui-card-action>
                </tum-ui-card-header>
                <tum-ui-card-content>Writing the summary document.</tum-ui-card-content>
            </tum-ui-card>
        `,
    }),
    play: async ({ canvas, canvasElement }) => {
        const heading = canvas.getByRole('heading', { level: 2, name: 'Progress' });
        await expect(heading).toBeTruthy();

        // The typography comes from the component's own class, so there is nothing for a host page's bare `h2`
        // rule to outrank — which is what removes the `!important` this used to require.
        const styles = getComputedStyle(heading);
        await expect(Number.parseFloat(styles.fontSize)).toBeGreaterThan(16);
        await expect(Number.parseInt(styles.fontWeight, 10)).toBeGreaterThanOrEqual(600);

        const header = canvasElement.querySelector('tum-ui-card-header')!;
        await expect(header.querySelector('tum-ui-card-action'), 'the action lives in the header').not.toBeNull();
        const body = canvasElement.querySelector('.tum-ui-card-body')!;
        await expect(body.contains(header), 'the header shares the padded body with the content').toBe(true);
    },
};
