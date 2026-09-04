import { moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect, userEvent, within } from 'storybook/test';

import { TumUiButtonComponent } from '../lib/button/tum-ui-button.component';
import { TumUiCardContentComponent, TumUiCardDescriptionComponent, TumUiCardHeaderComponent, TumUiCardTitleComponent } from '../lib/card/tum-ui-card-parts.component';
import { TumUiCardComponent } from '../lib/card/tum-ui-card.component';
import { TumUiEmptyContentComponent, TumUiEmptyDescriptionComponent, TumUiEmptyHeaderComponent, TumUiEmptyTitleComponent } from '../lib/empty/tum-ui-empty-parts.component';
import { TumUiEmptyComponent } from '../lib/empty/tum-ui-empty.component';
import { TumUiItemGroupComponent } from '../lib/item/tum-ui-item-group.component';
import { TumUiItemActionsComponent, TumUiItemContentComponent, TumUiItemDescriptionComponent, TumUiItemTitleComponent } from '../lib/item/tum-ui-item-parts.component';
import { TumUiItemComponent } from '../lib/item/tum-ui-item.component';
import { TumUiMessageComponent } from '../lib/message/tum-ui-message.component';
import { TumUiProseComponent } from '../lib/prose/tum-ui-prose.component';
import { TumUiSeparatorComponent } from '../lib/separator/tum-ui-separator.component';
import { TumUiSkeletonComponent } from '../lib/skeleton/tum-ui-skeleton.component';
import { TumUiTabListComponent } from '../lib/tabs/tum-ui-tab-list.component';
import { TumUiTabPanelComponent } from '../lib/tabs/tum-ui-tab-panel.component';
import { TumUiTabPanelsComponent } from '../lib/tabs/tum-ui-tab-panels.component';
import { TumUiTabComponent } from '../lib/tabs/tum-ui-tab.component';
import { TumUiTabsComponent } from '../lib/tabs/tum-ui-tabs.component';
import { TumUiTagComponent, TumUiTagSeverity } from '../lib/tag/tum-ui-tag.component';

interface ReviewFile {
    path: string;
    meta: string;
    /** The word beside the tag, so the change kind never reaches a reader through the tag's colour alone. */
    change: 'Added' | 'Modified' | 'Deleted';
}

interface DocumentReviewStoryArgs {
    /** Rendered HTML standing in for a document the job wrote. Sanitising it is the consumer's job, not the kit's. */
    summaryHtml: string;
    notesHtml: string;
    files: readonly ReviewFile[];
    loading: boolean;
    loadFailed: boolean;
    emptyTitle: string;
    emptyDescription: string;
    /** Present when the reader can do something about the empty state; absent when the copy can only name who can. */
    emptyAction?: string;
}

/** Colour is the second signal only: the word it is paired with is what actually carries the change kind. */
const CHANGE_SEVERITY: Record<ReviewFile['change'], TumUiTagSeverity> = {
    Added: 'success',
    Modified: 'info',
    Deleted: 'danger',
};

const summaryHtml = `
    <h2>What this job produced</h2>
    <p>Three source files and one test file, together with the notes below. Nothing has been published yet.</p>
    <h3>Approach</h3>
    <p>The work was split into a parser and a formatter so each can be tested on its own. The formatter reads a
    <code>Document</code> and returns a string; it holds no state.</p>
    <ul>
        <li>Parsing is total: an unreadable byte becomes a diagnostic, never an exception.</li>
        <li>Formatting is idempotent, which the round-trip test asserts.</li>
    </ul>
    <blockquote>Every figure below is a lower bound while the job is still running.</blockquote>
    <pre><code>format(parse(input)) === input</code></pre>
`;

const notesHtml = `
    <h2>Review notes</h2>
    <p>Two decisions are worth a second opinion before this is published.</p>
    <ol>
        <li>The formatter drops trailing whitespace, which changes existing files that relied on it.</li>
        <li>Diagnostics are collected rather than thrown, so a caller that ignores them sees a silent partial parse.</li>
    </ol>
`;

const files: ReviewFile[] = [
    { path: 'src/parser/tokenizer.ts', meta: '148 lines', change: 'Added' },
    { path: 'src/parser/document.ts', meta: '96 lines', change: 'Added' },
    { path: 'src/format/formatter.ts', meta: '61 lines', change: 'Modified' },
    { path: 'test/round-trip.spec.ts', meta: '38 lines', change: 'Added' },
    { path: 'src/legacy/print.ts', meta: 'removed', change: 'Deleted' },
];

/**
 * Composition of the package's building blocks into the screen that answers *"what did this job actually produce,
 * and is it good enough to publish?"* — a tabbed review surface holding two written documents and a list of files.
 *
 * It is the companion to `Patterns/Run status`: that page reports how a long-running job is going, this one reports
 * what it left behind. Together they are the two halves of a job's life, and they are the reason `prose`, `empty`,
 * `skeleton`, `separator` and `item` were added to the package — every one of them appears here, in the arrangement
 * it was designed for.
 *
 * Four contracts are on display and each is worth checking separately in review:
 *
 * - **`preserveContent`.** The panels are preserved rather than destroyed, so a trip to Files and back does not
 *   return the reader to the top of a document they had scrolled. `Preserved across tabs` proves it.
 * - **Loading reserves the box the content will occupy.** A `tum-ui-skeleton` inside an `aria-busy` container with
 *   an sr-only word — the placeholder itself is `aria-hidden`, because a bar is not a status.
 * - **Empty states carry an action, or name who can act.** Both shapes ship as stories, because the difference is
 *   the whole rule and it is invisible from one example.
 * - **A failed load is stated above the tabs, with its retry.** The fetch that failed is what fills two of the three
 *   panels, so a message inside one of them would be a message the reader has to go looking for.
 *
 * The documents are pre-rendered HTML on purpose. `tum-ui-prose` owns typography and nothing else: it never parses
 * markdown and never sanitises, because a design system that owned a markdown pipeline would own the application's
 * dependency list too.
 */
const meta = {
    title: 'Patterns/Document review',
    decorators: [
        moduleMetadata({
            imports: [
                TumUiButtonComponent,
                TumUiCardComponent,
                TumUiCardContentComponent,
                TumUiCardDescriptionComponent,
                TumUiCardHeaderComponent,
                TumUiCardTitleComponent,
                TumUiEmptyComponent,
                TumUiEmptyContentComponent,
                TumUiEmptyDescriptionComponent,
                TumUiEmptyHeaderComponent,
                TumUiEmptyTitleComponent,
                TumUiItemActionsComponent,
                TumUiItemComponent,
                TumUiItemContentComponent,
                TumUiItemDescriptionComponent,
                TumUiItemGroupComponent,
                TumUiItemTitleComponent,
                TumUiMessageComponent,
                TumUiProseComponent,
                TumUiSeparatorComponent,
                TumUiSkeletonComponent,
                TumUiTabComponent,
                TumUiTabListComponent,
                TumUiTabPanelComponent,
                TumUiTabPanelsComponent,
                TumUiTabsComponent,
                TumUiTagComponent,
            ],
        }),
    ],
    args: {
        summaryHtml,
        notesHtml,
        files,
        loading: false,
        loadFailed: false,
        emptyTitle: 'Nothing written yet',
        emptyDescription: 'Documents appear here as the job finishes each stage.',
    },
    parameters: {
        layout: 'padded',
    },
    render: (args) => ({
        props: { ...args, changeSeverity: CHANGE_SEVERITY },
        template: `
            <tum-ui-card class="tum-ui-story-review-card">
                <tum-ui-card-header>
                    <tum-ui-card-title [level]="2">Output</tum-ui-card-title>
                    <tum-ui-card-description>Everything this job wrote. None of it is published.</tum-ui-card-description>
                </tum-ui-card-header>

                <tum-ui-card-content>
                    @if (loadFailed) {
                        <tum-ui-message severity="danger" class="tum-ui-story-review-alert">
                            <div class="tum-ui-story-review-alert-row">
                                <span>The saved output could not be loaded. Nothing the job produced has been lost.</span>
                                <tum-ui-button size="small" severity="secondary" variant="outlined">Try again</tum-ui-button>
                            </div>
                        </tum-ui-message>
                    }

                    <tum-ui-tabs value="summary">
                        <tum-ui-tab-list>
                            <tum-ui-tab value="summary">Summary</tum-ui-tab>
                            <tum-ui-tab value="notes">Notes</tum-ui-tab>
                            <tum-ui-tab value="files">
                                <span class="tum-ui-story-review-tab-label">
                                    <span>Files</span>
                                    @if (files.length) {
                                        <tum-ui-tag variant="quiet" size="small" [value]="'' + files.length" />
                                    }
                                </span>
                            </tum-ui-tab>
                        </tum-ui-tab-list>

                        <tum-ui-tab-panels>
                            <tum-ui-tab-panel value="summary" class="tum-ui-story-review-panel" preserveContent>
                                @if (loading) {
                                    <div aria-busy="true">
                                        <span class="tum:sr-only">Loading the summary</span>
                                        <tum-ui-skeleton width="14rem" height="1.5rem" />
                                        <div class="tum-ui-story-review-skeleton-gap"></div>
                                        <tum-ui-skeleton [lines]="4" />
                                    </div>
                                } @else if (summaryHtml) {
                                    <tum-ui-prose [innerHTML]="summaryHtml"></tum-ui-prose>
                                } @else {
                                    <tum-ui-empty size="small">
                                        <tum-ui-empty-header>
                                            <tum-ui-empty-title>{{ emptyTitle }}</tum-ui-empty-title>
                                            <tum-ui-empty-description>{{ emptyDescription }}</tum-ui-empty-description>
                                        </tum-ui-empty-header>
                                        @if (emptyAction) {
                                            <tum-ui-empty-content>
                                                <tum-ui-button size="small">{{ emptyAction }}</tum-ui-button>
                                            </tum-ui-empty-content>
                                        }
                                    </tum-ui-empty>
                                }
                            </tum-ui-tab-panel>

                            <tum-ui-tab-panel value="notes" class="tum-ui-story-review-panel" preserveContent>
                                @if (notesHtml) {
                                    <tum-ui-prose density="compact" [innerHTML]="notesHtml"></tum-ui-prose>
                                } @else {
                                    <tum-ui-empty size="small">
                                        <tum-ui-empty-header>
                                            <tum-ui-empty-title>No notes</tum-ui-empty-title>
                                            <tum-ui-empty-description>Notes are written last, once every check has run.</tum-ui-empty-description>
                                        </tum-ui-empty-header>
                                    </tum-ui-empty>
                                }
                            </tum-ui-tab-panel>

                            <tum-ui-tab-panel value="files" class="tum-ui-story-review-panel" preserveContent>
                                @if (loading) {
                                    <div aria-busy="true">
                                        <span class="tum:sr-only">Loading the file list</span>
                                        <tum-ui-skeleton [lines]="5" />
                                    </div>
                                } @else if (files.length) {
                                    <div class="tum-ui-story-review-split">
                                        <tum-ui-item-group ariaLabel="Files this job wrote" size="small" [separators]="true">
                                            @for (file of files; track file.path) {
                                                <tum-ui-item>
                                                    <tum-ui-item-content>
                                                        <tum-ui-item-title>{{ file.path }}</tum-ui-item-title>
                                                        <tum-ui-item-description>{{ file.meta }}</tum-ui-item-description>
                                                    </tum-ui-item-content>
                                                    <tum-ui-item-actions>
                                                        <tum-ui-tag variant="quiet" size="small" [severity]="changeSeverity[file.change]" [value]="file.change" />
                                                    </tum-ui-item-actions>
                                                </tum-ui-item>
                                            }
                                        </tum-ui-item-group>

                                        <tum-ui-separator orientation="vertical" class="tum-ui-story-review-rule" />

                                        <div class="tum-ui-story-review-detail">
                                            <tum-ui-empty size="small">
                                                <tum-ui-empty-header>
                                                    <tum-ui-empty-title>No file selected</tum-ui-empty-title>
                                                    <tum-ui-empty-description>Pick a file on the left to read what changed in it.</tum-ui-empty-description>
                                                </tum-ui-empty-header>
                                            </tum-ui-empty>
                                        </div>
                                    </div>
                                } @else {
                                    <tum-ui-empty size="small">
                                        <tum-ui-empty-header>
                                            <tum-ui-empty-title>No files</tum-ui-empty-title>
                                            <tum-ui-empty-description>{{ emptyDescription }}</tum-ui-empty-description>
                                        </tum-ui-empty-header>
                                    </tum-ui-empty>
                                }
                            </tum-ui-tab-panel>
                        </tum-ui-tab-panels>
                    </tum-ui-tabs>
                </tum-ui-card-content>
            </tum-ui-card>
        `,
    }),
} satisfies Meta<DocumentReviewStoryArgs>;

export default meta;

type Story = StoryObj<DocumentReviewStoryArgs>;

/** The state the surface exists for: the document is rendered, at the reading measure, and open by default. */
export const Reading: Story = {};

/**
 * Measured proof that the rendered HTML is bound to the prose element itself and not to a wrapper inside it.
 *
 * This is the one mistake this pattern can make that looks fine in a screenshot. The vertical rhythm is set by
 * direct-child rules (`.tum-ui-prose > * + *`) while the type rules are descendant rules, so one `<div>` in between
 * keeps every heading and paragraph styled and silently drops all the spacing between them. The assertion is
 * therefore structural — every block the document arrived with is a direct child — rather than visual.
 */
export const ProseIsTheDirectParent: Story = {
    tags: ['!dev', '!autodocs'],
    play: async ({ canvasElement }) => {
        const prose = canvasElement.querySelector('tum-ui-prose')!;
        await expect(prose, 'the summary is rendered').not.toBeNull();

        // Top-level blocks only: a nested `<ul>` inside an `<li>` is a grandchild by construction and is spaced by
        // the list rules instead, so it is not evidence either way.
        const topLevel = Array.from(prose.children).map((child) => child.tagName.toLowerCase());
        await expect(topLevel.length, 'the fixture carries several blocks, or this proves nothing').toBeGreaterThan(2);
        await expect(topLevel, 'a heading sits directly on the prose element').toContain('h2');
        await expect(topLevel, 'and so does a paragraph').toContain('p');
        await expect(topLevel, 'nothing is wrapped in a generic element that would break `> * + *`').not.toContain('div');
    },
};

/**
 * The first fetch. Each placeholder occupies the box its content will occupy, so nothing moves when the data lands,
 * and the announcement lives on the `aria-busy` container rather than on the bars.
 */
export const Loading: Story = {
    args: { loading: true },
};

/** Empty and the reader can act, so the empty state carries the control rather than describing where to find it. */
export const EmptyWithAction: Story = {
    args: {
        summaryHtml: '',
        notesHtml: '',
        files: [],
        emptyTitle: 'This job produced nothing',
        emptyDescription: 'It stopped before it wrote anything. Starting it again is safe; nothing was published.',
        emptyAction: 'Run it again',
    },
};

/**
 * Empty and the reader cannot act. The copy then names what fills the region and when — the one alternative to an
 * action that is not an apology. A disabled button here would be worse than no button.
 */
export const EmptyNamesWhoCanAct: Story = {
    args: {
        summaryHtml: '',
        notesHtml: '',
        files: [],
        emptyTitle: 'Nothing written yet',
        emptyDescription: 'The job is still on its first stage. The summary is written once the last check has run.',
    },
};

/**
 * The load failed. The cause is in plain language, the fate of the work is stated, and the retry sits next to both —
 * above the tabs, because the fetch that failed is what fills two of the three panels.
 */
export const LoadFailed: Story = {
    args: {
        loadFailed: true,
        summaryHtml: '',
        notesHtml: '',
        emptyTitle: 'The summary could not be loaded',
        emptyDescription: 'It is still on the server. Try again above.',
    },
};

/** The list and its detail pane, divided by a `decorative` rule — a `role="separator"` here would be list noise. */
export const FileList: Story = {
    args: { summaryHtml: '', notesHtml: '' },
};

/**
 * The reason `preserveContent` exists. A trip to Files and back leaves the summary exactly as it was, so a reader
 * who had scrolled halfway through a long document is not returned to the top of it.
 *
 * The assertion is on the DOM rather than on a screenshot: the inactive panel keeps its children, and is `hidden`
 * and `inert` so neither the accessibility tree nor the tab order can reach the copy behind the active tab.
 */
export const PreservedAcrossTabs: Story = {
    tags: ['!autodocs'],
    play: async ({ canvasElement }) => {
        const canvas = within(canvasElement);
        const summaryPanel = canvasElement.querySelector('tum-ui-tab-panel[data-state="active"]')!;
        await expect(summaryPanel.querySelector('tum-ui-prose'), 'the summary renders while it is the active tab').not.toBeNull();

        await userEvent.click(canvas.getByRole('tab', { name: /Files/ }));

        await expect(summaryPanel.getAttribute('data-state')).toBe('inactive');
        await expect(summaryPanel.querySelector('tum-ui-prose'), 'a preserved panel keeps its content').not.toBeNull();
        await expect(summaryPanel.hasAttribute('hidden'), 'and is out of the accessibility tree').toBe(true);
        await expect(summaryPanel.hasAttribute('inert'), 'and out of the tab order').toBe(true);

        await userEvent.click(canvas.getByRole('tab', { name: 'Summary' }));
        await expect(summaryPanel.getAttribute('data-state')).toBe('active');
    },
};
