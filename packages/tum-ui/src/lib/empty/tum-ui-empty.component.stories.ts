import { moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect } from 'storybook/test';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faFileLines } from '@fortawesome/free-solid-svg-icons';

import { TumUiButtonComponent } from '../button/tum-ui-button.component';
import { TumUiCardComponent } from '../card/tum-ui-card.component';
import { TumUiPanelComponent } from '../panel/tum-ui-panel.component';
import {
    TumUiEmptyContentComponent,
    TumUiEmptyDescriptionComponent,
    TumUiEmptyHeaderComponent,
    TumUiEmptyMediaComponent,
    TumUiEmptyTitleComponent,
} from './tum-ui-empty-parts.component';
import { TumUiEmptyComponent } from './tum-ui-empty.component';

const imports = [
    TumUiEmptyComponent,
    TumUiEmptyHeaderComponent,
    TumUiEmptyMediaComponent,
    TumUiEmptyTitleComponent,
    TumUiEmptyDescriptionComponent,
    TumUiEmptyContentComponent,
    TumUiButtonComponent,
    TumUiCardComponent,
    TumUiPanelComponent,
    FaIconComponent,
];

const meta = {
    title: 'Data Display/Empty',
    component: TumUiEmptyComponent,
    decorators: [moduleMetadata({ imports })],
    args: {
        size: 'medium',
    },
    argTypes: {
        size: { control: 'inline-radio', options: ['small', 'medium', 'large'] },
    },
    parameters: {
        layout: 'padded',
    },
} satisfies Meta<TumUiEmptyComponent>;

export default meta;

type Story = StoryObj<TumUiEmptyComponent>;

const withAction = (size: string | undefined) => ({
    props: { size, faFileLines },
    template: `
        <tum-ui-empty [size]="size">
            <tum-ui-empty-header>
                <tum-ui-empty-media variant="icon"><fa-icon [icon]="faFileLines" /></tum-ui-empty-media>
                <tum-ui-empty-title>No files yet</tum-ui-empty-title>
                <tum-ui-empty-description>Files written during a run appear here as soon as the first one lands.</tum-ui-empty-description>
            </tum-ui-empty-header>
            <tum-ui-empty-content>
                <tum-ui-button size="small">Start a run</tum-ui-button>
            </tum-ui-empty-content>
        </tum-ui-empty>
    `,
});

/**
 * The default shape: what is missing, why, and the control that resolves it. An empty state that offers neither an
 * action nor the name of someone who has one is an apology.
 */
export const Default: Story = {
    render: ({ size }) => withAction(size),
};

/** Without media. The title carries the state on its own where a glyph would only repeat it. */
export const TitleOnly: Story = {
    render: () => ({
        template: `
            <tum-ui-empty>
                <tum-ui-empty-header>
                    <tum-ui-empty-title>Nothing to review</tum-ui-empty-title>
                </tum-ui-empty-header>
            </tum-ui-empty>
        `,
    }),
};

/**
 * The reader cannot act, so the state names who can. This is the other half of the rule: an empty state carries an
 * action, or it names the role that has one — never nothing.
 */
export const NamesWhoCanAct: Story = {
    render: () => ({
        props: { faFileLines },
        template: `
            <tum-ui-empty>
                <tum-ui-empty-header>
                    <tum-ui-empty-media variant="icon"><fa-icon [icon]="faFileLines" /></tum-ui-empty-media>
                    <tum-ui-empty-title>No results have been published</tum-ui-empty-title>
                    <tum-ui-empty-description>The owner of this workspace publishes results once every review has finished.</tum-ui-empty-description>
                </tum-ui-empty-header>
            </tum-ui-empty>
        `,
    }),
};

/** Bare artwork rather than a framed glyph: an illustration brings its own frame, so the slot only centres it. */
export const IllustrationMedia: Story = {
    render: () => ({
        template: `
            <tum-ui-empty>
                <tum-ui-empty-header>
                    <tum-ui-empty-media>
                        <svg width="96" height="64" viewBox="0 0 96 64" fill="none" stroke="currentColor" stroke-width="2">
                            <rect x="8" y="12" width="80" height="44" rx="6" />
                            <path d="M8 26h80M28 12v44" />
                        </svg>
                    </tum-ui-empty-media>
                    <tum-ui-empty-title>Nothing selected</tum-ui-empty-title>
                    <tum-ui-empty-description>Choose an item on the left to see it here.</tum-ui-empty-description>
                </tum-ui-empty-header>
            </tum-ui-empty>
        `,
    }),
};

/** Every size in one column, so the vertical room each one claims is comparable at a glance. */
export const Sizes: Story = {
    render: () => ({
        props: { sizes: ['small', 'medium', 'large'], faFileLines },
        template: `
            <div style="display: grid; gap: 1rem;">
                @for (size of sizes; track size) {
                    <div style="border: 1px dashed var(--tumaet-ui-border-color); border-radius: 0.5rem;">
                        <tum-ui-empty [size]="size">
                            <tum-ui-empty-header>
                                <tum-ui-empty-media variant="icon"><fa-icon [icon]="faFileLines" /></tum-ui-empty-media>
                                <tum-ui-empty-title>{{ size }}</tum-ui-empty-title>
                            </tum-ui-empty-header>
                        </tum-ui-empty>
                    </div>
                }
            </div>
        `,
    }),
};

/** In a card, at `small`, which is what a region-sized placeholder inside a bounded surface should be. */
export const InsideACard: Story = {
    render: () => ({
        props: { faFileLines },
        template: `
            <tum-ui-card variant="outline" style="display: block; width: 26rem;">
                <tum-ui-empty size="small">
                    <tum-ui-empty-header>
                        <tum-ui-empty-media variant="icon"><fa-icon [icon]="faFileLines" /></tum-ui-empty-media>
                        <tum-ui-empty-title>No files yet</tum-ui-empty-title>
                        <tum-ui-empty-description>The agent has not written anything.</tum-ui-empty-description>
                    </tum-ui-empty-header>
                </tum-ui-empty>
            </tum-ui-card>
        `,
    }),
};

/** Inside a panel, where the panel's own header already states what the region is. */
export const InsideAPanel: Story = {
    render: () => ({
        template: `
            <tum-ui-panel header="Attachments" style="display: block; width: 26rem;">
                <tum-ui-empty size="small">
                    <tum-ui-empty-header>
                        <tum-ui-empty-title>No attachments</tum-ui-empty-title>
                    </tum-ui-empty-header>
                </tum-ui-empty>
            </tum-ui-panel>
        `,
    }),
};

/**
 * Measured proof of the two contracts that decide whether this component is correct: the placeholder announces
 * nothing of its own, and its title is not a heading. Both are invisible in a screenshot and both go wrong quietly.
 */
export const AccessibilityContract: Story = {
    tags: ['!dev', '!autodocs'],
    render: () => withAction('medium'),
    play: async ({ canvasElement }) => {
        const root = canvasElement.querySelector('tum-ui-empty')!;
        await expect(root.getAttribute('role'), 'an empty state is ambient, not an announcement').toBeNull();
        await expect(root.getAttribute('aria-live')).toBeNull();
        await expect(root.querySelector('h1, h2, h3, h4, h5, h6'), 'the title is not a heading').toBeNull();
        await expect(root.querySelector('[role="heading"]')).toBeNull();
        await expect(root.querySelector('tum-ui-empty-media')!.getAttribute('aria-hidden')).toBe('true');
        await expect(root.querySelector('tum-ui-empty-content button'), 'the state carries an action').not.toBeNull();
    },
};
