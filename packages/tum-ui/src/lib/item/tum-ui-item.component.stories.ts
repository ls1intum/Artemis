import { moduleMetadata } from '@storybook/angular-vite';
import type { Meta, StoryObj } from '@storybook/angular-vite';
import { expect, userEvent } from 'storybook/test';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faFileLines } from '@fortawesome/free-solid-svg-icons';

import { TumUiButtonComponent } from '../button/tum-ui-button.component';
import { TumUiSeparatorComponent } from '../separator/tum-ui-separator.component';
import { TumUiStatusDotComponent } from '../status-dot/tum-ui-status-dot.component';
import { TumUiTagComponent } from '../tag/tum-ui-tag.component';
import { TumUiItemGroupComponent, TumUiItemGroupDirective } from './tum-ui-item-group.component';
import {
    TumUiItemActionsComponent,
    TumUiItemContentComponent,
    TumUiItemDescriptionComponent,
    TumUiItemMediaComponent,
    TumUiItemTitleComponent,
} from './tum-ui-item-parts.component';
import { TumUiItemComponent, TumUiItemDirective } from './tum-ui-item.component';

const imports = [
    TumUiItemGroupComponent,
    TumUiItemGroupDirective,
    TumUiItemComponent,
    TumUiItemDirective,
    TumUiItemMediaComponent,
    TumUiItemContentComponent,
    TumUiItemTitleComponent,
    TumUiItemDescriptionComponent,
    TumUiItemActionsComponent,
    TumUiTagComponent,
    TumUiButtonComponent,
    TumUiStatusDotComponent,
    TumUiSeparatorComponent,
    FaIconComponent,
];

const files = [
    { path: 'src/main/java/Sorter.java', detail: 'Modified two minutes ago', state: 'Modified', severity: 'info' },
    { path: 'src/test/java/SorterTest.java', detail: 'Added two minutes ago', state: 'Added', severity: 'success' },
    { path: 'README.md', detail: 'Deleted a minute ago', state: 'Deleted', severity: 'danger' },
];

const meta = {
    title: 'Data Display/Item',
    component: TumUiItemComponent,
    decorators: [moduleMetadata({ imports })],
    args: {
        variant: 'default',
    },
    argTypes: {
        variant: { control: 'inline-radio', options: ['default', 'outline', 'muted'] },
        size: { control: 'inline-radio', options: [undefined, 'small', 'medium', 'large'] },
    },
    parameters: {
        layout: 'padded',
    },
} satisfies Meta<TumUiItemComponent>;

export default meta;

type Story = StoryObj<TumUiItemComponent>;

/** One row, on its own. Media on the left, the text column in the middle, actions pinned right. */
export const Default: Story = {
    render: (args) => ({
        props: { ...args, faFileLines },
        template: `
            <tum-ui-item [variant]="variant" [size]="size" style="width: 32rem;">
                <tum-ui-item-media variant="icon"><fa-icon [icon]="faFileLines" /></tum-ui-item-media>
                <tum-ui-item-content>
                    <tum-ui-item-title>src/main/java/Sorter.java</tum-ui-item-title>
                    <tum-ui-item-description>Modified two minutes ago</tum-ui-item-description>
                </tum-ui-item-content>
                <tum-ui-item-actions><tum-ui-tag size="small" variant="quiet" severity="info" value="Modified" /></tum-ui-item-actions>
            </tum-ui-item>
        `,
    }),
};

/** A named list. The group keeps the list semantics a flex container would otherwise strip. */
export const Group: Story = {
    render: () => ({
        props: { files, faFileLines },
        template: `
            <tum-ui-item-group ariaLabel="Changed files" style="width: 32rem;">
                @for (file of files; track file.path) {
                    <tum-ui-item>
                        <tum-ui-item-media variant="icon"><fa-icon [icon]="faFileLines" /></tum-ui-item-media>
                        <tum-ui-item-content>
                            <tum-ui-item-title>{{ file.path }}</tum-ui-item-title>
                            <tum-ui-item-description>{{ file.detail }}</tum-ui-item-description>
                        </tum-ui-item-content>
                        <tum-ui-item-actions><tum-ui-tag size="small" variant="quiet" [severity]="file.severity" [value]="file.state" /></tum-ui-item-actions>
                    </tum-ui-item>
                }
            </tum-ui-item-group>
        `,
    }),
};

/**
 * Rules between rows are a group setting, not an element the consumer inserts. As markup a separator would put a
 * non-row child inside `role="list"` on every gap; as a border on the row it costs the list nothing.
 */
export const GroupedWithSeparators: Story = {
    render: () => ({
        props: { files },
        template: `
            <tum-ui-item-group ariaLabel="Changed files" separators style="width: 32rem;">
                @for (file of files; track file.path) {
                    <tum-ui-item>
                        <tum-ui-item-content>
                            <tum-ui-item-title>{{ file.path }}</tum-ui-item-title>
                            <tum-ui-item-description>{{ file.detail }}</tum-ui-item-description>
                        </tum-ui-item-content>
                    </tum-ui-item>
                }
            </tum-ui-item-group>
        `,
    }),
};

/**
 * The whole row is a link. This is the composition the directive exists for: a native `<ul>`/`<li>`, an `<a>`
 * carrying the layout, and no role written over the anchor's own — so it is still announced as a link and still
 * reachable by the keyboard.
 */
export const LinkRows: Story = {
    render: () => ({
        props: {
            runs: [
                { id: 1, title: 'Sorting algorithms', detail: 'Finished 12 minutes ago', state: 'success', word: 'Succeeded' },
                { id: 2, title: 'Graph traversal', detail: 'Running for 4 minutes', state: 'running', word: 'Running' },
                { id: 3, title: 'Dynamic programming', detail: 'Failed an hour ago', state: 'danger', word: 'Failed' },
            ],
        },
        template: `
            <ul tumUiItemGroup ariaLabel="Recent runs" separators style="width: 32rem;">
                @for (run of runs; track run.id) {
                    <li>
                        <a tumUiItem href="#run-{{ run.id }}">
                            <tum-ui-item-media><tum-ui-status-dot [state]="run.state" [label]="run.word" [showLabel]="false" /></tum-ui-item-media>
                            <tum-ui-item-content>
                                <tum-ui-item-title>{{ run.title }}</tum-ui-item-title>
                                <tum-ui-item-description>{{ run.detail }}</tum-ui-item-description>
                            </tum-ui-item-content>
                        </a>
                    </li>
                }
            </ul>
        `,
    }),
};

/** Every size, published once by the group rather than repeated on every row. */
export const Sizes: Story = {
    render: () => ({
        props: { sizes: ['small', 'medium', 'large'] },
        template: `
            <div style="display: grid; gap: 1.5rem; width: 32rem;">
                @for (size of sizes; track size) {
                    <tum-ui-item-group [ariaLabel]="size + ' rows'" [size]="size">
                        <tum-ui-item>
                            <tum-ui-item-content>
                                <tum-ui-item-title>{{ size }}</tum-ui-item-title>
                                <tum-ui-item-description>Row height follows the group.</tum-ui-item-description>
                            </tum-ui-item-content>
                        </tum-ui-item>
                    </tum-ui-item-group>
                }
            </div>
        `,
    }),
};

/** Every surface treatment side by side. */
export const Variants: Story = {
    render: () => ({
        props: { variants: ['default', 'outline', 'muted'] },
        template: `
            <div style="display: grid; gap: 0.75rem; width: 32rem;">
                @for (variant of variants; track variant) {
                    <tum-ui-item [variant]="variant">
                        <tum-ui-item-content><tum-ui-item-title>{{ variant }}</tum-ui-item-title></tum-ui-item-content>
                        <tum-ui-item-actions><tum-ui-button size="small" variant="text">Open</tum-ui-button></tum-ui-item-actions>
                    </tum-ui-item>
                }
            </div>
        `,
    }),
};

/**
 * A row narrower than its content. The title truncates and the action stays put; the opposite — actions pushed off
 * the row by a long path — is what `min-w-0` on the text column prevents.
 */
export const LongTruncatingContent: Story = {
    render: () => ({
        template: `
            <tum-ui-item variant="outline" style="width: 22rem;">
                <tum-ui-item-content>
                    <tum-ui-item-title>src/main/java/com/example/platform/ingestion/VeryDeeplyNestedSorter.java</tum-ui-item-title>
                    <tum-ui-item-description>Modified two minutes ago by the background agent, in the primary repository</tum-ui-item-description>
                </tum-ui-item-content>
                <tum-ui-item-actions><tum-ui-tag size="small" variant="quiet" value="Modified" /></tum-ui-item-actions>
            </tum-ui-item>
        `,
    }),
};

/**
 * A row that has its own state. The row publishes only its variant axes; anything domain-specific — "writing now",
 * "selected", "stale" — is the consumer's own `data-*` attribute, which the row's class list is built not to fight.
 */
export const ConsumerState: Story = {
    render: () => ({
        template: `
            <style>
                .story-file-list tum-ui-item[data-active='true'] {
                    background: color-mix(in srgb, var(--tumaet-ui-state-info) 12%, transparent);
                }
            </style>
            <tum-ui-item-group class="story-file-list" ariaLabel="Changed files" separators style="width: 32rem;">
                <tum-ui-item data-active="true">
                    <tum-ui-item-content><tum-ui-item-title>src/main/java/Sorter.java</tum-ui-item-title></tum-ui-item-content>
                    <tum-ui-item-actions><tum-ui-tag size="small" variant="quiet" severity="info" value="Writing now" /></tum-ui-item-actions>
                </tum-ui-item>
                <tum-ui-item>
                    <tum-ui-item-content><tum-ui-item-title>README.md</tum-ui-item-title></tum-ui-item-content>
                </tum-ui-item>
            </tum-ui-item-group>
        `,
    }),
};

/**
 * Measured proof of the contract that decides whether the directive form is correct: an anchor row is still a link
 * and still keyboard-reachable, while a plain host publishes the `listitem` role that flex layout strips.
 */
export const KeyboardWalk: Story = {
    tags: ['!dev', '!autodocs'],
    render: () => ({
        template: `
            <ul tumUiItemGroup ariaLabel="Recent runs" separators style="width: 24rem;">
                <li><a tumUiItem href="#one">First run</a></li>
                <li><a tumUiItem href="#two">Second run</a></li>
                <li tumUiItem>A row that is not a link</li>
            </ul>
        `,
    }),
    play: async ({ canvas, canvasElement }) => {
        const list = canvasElement.querySelector('ul')!;
        await expect(list.getAttribute('role')).toBe('list');
        await expect(list.getAttribute('aria-label')).toBe('Recent runs');

        const first = canvas.getByRole('link', { name: 'First run' });
        const second = canvas.getByRole('link', { name: 'Second run' });
        await expect(first.getAttribute('role'), 'a link row keeps its own role').toBeNull();

        first.focus();
        await expect(document.activeElement).toBe(first);
        await userEvent.tab();
        await expect(document.activeElement, 'Tab walks from row to row').toBe(second);

        const plain = canvasElement.querySelector('li[tumUiItem]')!;
        await expect(plain.getAttribute('role'), 'a non-interactive host gets the list semantics back').toBe('listitem');
    },
};
