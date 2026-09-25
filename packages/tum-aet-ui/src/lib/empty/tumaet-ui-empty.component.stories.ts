import { moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect } from 'storybook/test';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faFileLines } from '@fortawesome/free-solid-svg-icons';

import { TumAetUiButtonComponent } from '../button/tumaet-ui-button.component';
import { TumAetUiCardComponent } from '../card/tumaet-ui-card.component';
import { TumAetUiPanelComponent } from '../panel/tumaet-ui-panel.component';
import {
    TumAetUiEmptyContentComponent,
    TumAetUiEmptyDescriptionComponent,
    TumAetUiEmptyHeaderComponent,
    TumAetUiEmptyMediaComponent,
    TumAetUiEmptyTitleComponent,
} from './tumaet-ui-empty-parts.component';
import { TumAetUiEmptyComponent } from './tumaet-ui-empty.component';

const imports = [
    TumAetUiEmptyComponent,
    TumAetUiEmptyHeaderComponent,
    TumAetUiEmptyMediaComponent,
    TumAetUiEmptyTitleComponent,
    TumAetUiEmptyDescriptionComponent,
    TumAetUiEmptyContentComponent,
    TumAetUiButtonComponent,
    TumAetUiCardComponent,
    TumAetUiPanelComponent,
    FaIconComponent,
];

const meta = {
    title: 'Data Display/Empty',
    component: TumAetUiEmptyComponent,
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
} satisfies Meta<TumAetUiEmptyComponent>;

export default meta;

type Story = StoryObj<TumAetUiEmptyComponent>;

const withAction = (size: string | undefined) => ({
    props: { size, faFileLines },
    template: `
        <tumaet-ui-empty [size]="size">
            <tumaet-ui-empty-header>
                <tumaet-ui-empty-media variant="icon"><fa-icon [icon]="faFileLines" /></tumaet-ui-empty-media>
                <tumaet-ui-empty-title>No files yet</tumaet-ui-empty-title>
                <tumaet-ui-empty-description>Files written during a run appear here as soon as the first one lands.</tumaet-ui-empty-description>
            </tumaet-ui-empty-header>
            <tumaet-ui-empty-content>
                <tumaet-ui-button size="small">Start a run</tumaet-ui-button>
            </tumaet-ui-empty-content>
        </tumaet-ui-empty>
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
            <tumaet-ui-empty>
                <tumaet-ui-empty-header>
                    <tumaet-ui-empty-title>Nothing to review</tumaet-ui-empty-title>
                </tumaet-ui-empty-header>
            </tumaet-ui-empty>
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
            <tumaet-ui-empty>
                <tumaet-ui-empty-header>
                    <tumaet-ui-empty-media variant="icon"><fa-icon [icon]="faFileLines" /></tumaet-ui-empty-media>
                    <tumaet-ui-empty-title>No results have been published</tumaet-ui-empty-title>
                    <tumaet-ui-empty-description>The owner of this workspace publishes results once every review has finished.</tumaet-ui-empty-description>
                </tumaet-ui-empty-header>
            </tumaet-ui-empty>
        `,
    }),
};

/** Bare artwork rather than a framed glyph: an illustration brings its own frame, so the slot only centres it. */
export const IllustrationMedia: Story = {
    render: () => ({
        template: `
            <tumaet-ui-empty>
                <tumaet-ui-empty-header>
                    <tumaet-ui-empty-media>
                        <svg width="96" height="64" viewBox="0 0 96 64" fill="none" stroke="currentColor" stroke-width="2">
                            <rect x="8" y="12" width="80" height="44" rx="6" />
                            <path d="M8 26h80M28 12v44" />
                        </svg>
                    </tumaet-ui-empty-media>
                    <tumaet-ui-empty-title>Nothing selected</tumaet-ui-empty-title>
                    <tumaet-ui-empty-description>Choose an item on the left to see it here.</tumaet-ui-empty-description>
                </tumaet-ui-empty-header>
            </tumaet-ui-empty>
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
                        <tumaet-ui-empty [size]="size">
                            <tumaet-ui-empty-header>
                                <tumaet-ui-empty-media variant="icon"><fa-icon [icon]="faFileLines" /></tumaet-ui-empty-media>
                                <tumaet-ui-empty-title>{{ size }}</tumaet-ui-empty-title>
                            </tumaet-ui-empty-header>
                        </tumaet-ui-empty>
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
            <tumaet-ui-card variant="outline" style="display: block; width: 26rem;">
                <tumaet-ui-empty size="small">
                    <tumaet-ui-empty-header>
                        <tumaet-ui-empty-media variant="icon"><fa-icon [icon]="faFileLines" /></tumaet-ui-empty-media>
                        <tumaet-ui-empty-title>No files yet</tumaet-ui-empty-title>
                        <tumaet-ui-empty-description>The agent has not written anything.</tumaet-ui-empty-description>
                    </tumaet-ui-empty-header>
                </tumaet-ui-empty>
            </tumaet-ui-card>
        `,
    }),
};

/** Inside a panel, where the panel's own header already states what the region is. */
export const InsideAPanel: Story = {
    render: () => ({
        template: `
            <tumaet-ui-panel header="Attachments" style="display: block; width: 26rem;">
                <tumaet-ui-empty size="small">
                    <tumaet-ui-empty-header>
                        <tumaet-ui-empty-title>No attachments</tumaet-ui-empty-title>
                    </tumaet-ui-empty-header>
                </tumaet-ui-empty>
            </tumaet-ui-panel>
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
        const root = canvasElement.querySelector('tumaet-ui-empty')!;
        await expect(root.getAttribute('role'), 'an empty state is ambient, not an announcement').toBeNull();
        await expect(root.getAttribute('aria-live')).toBeNull();
        await expect(root.querySelector('h1, h2, h3, h4, h5, h6'), 'the title is not a heading').toBeNull();
        await expect(root.querySelector('[role="heading"]')).toBeNull();
        await expect(root.querySelector('tumaet-ui-empty-media')!.getAttribute('aria-hidden')).toBe('true');
        await expect(root.querySelector('tumaet-ui-empty-content button'), 'the state carries an action').not.toBeNull();
    },
};
