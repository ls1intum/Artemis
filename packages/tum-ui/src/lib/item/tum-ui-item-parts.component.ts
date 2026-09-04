import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** Presentation of a row's leading graphic. */
export type TumUiItemMediaVariant = 'default' | 'icon';

const MEDIA_BASE = 'tum-ui-item-media tum:flex tum:shrink-0 tum:items-center tum:justify-center tum:text-muted';

const MEDIA_VARIANT: Record<TumUiItemMediaVariant, string> = {
    // An avatar, a thumbnail or a status dot arrives at its own size.
    default: '',
    // A single glyph gets a fixed box, so a column of rows keeps one text edge whatever the glyph's width.
    icon: 'tum:size-8 tum:rounded-md tum:bg-hover-background',
};

/**
 * Leading graphic of a row.
 *
 * Not `aria-hidden` by default, because the graphic is sometimes the row's only statement of state — hide it
 * yourself when it merely repeats the title, and give it an accessible name when it does not.
 */
@Component({
    selector: 'tum-ui-item-media',
    template: '<ng-content />',
    host: {
        '[class]': 'hostClasses()',
        '[attr.data-slot]': '"item-media"',
        '[attr.data-variant]': 'variant()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiItemMediaComponent {
    /** `icon` gives a single glyph a fixed, tinted box; `default` lets the content size itself. */
    readonly variant = input<TumUiItemMediaVariant>('default');

    protected readonly hostClasses = computed(() => `${MEDIA_BASE} ${MEDIA_VARIANT[this.variant()]}`.trimEnd());
}

/**
 * The text column of a row.
 *
 * `min-w-0` is the load-bearing part: without it a flex child refuses to shrink below its content, so a long path
 * pushes the trailing actions off the row instead of truncating.
 */
@Component({
    selector: 'tum-ui-item-content',
    template: '<ng-content />',
    host: {
        class: 'tum-ui-item-content tum:flex tum:min-w-0 tum:flex-1 tum:flex-col tum:gap-0.5',
        '[attr.data-slot]': '"item-content"',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiItemContentComponent {}

/**
 * The row's primary line.
 *
 * It is not a heading: a list of forty rows is not forty sections, and forty headings would flood a screen
 * reader's outline with the list's own contents.
 */
@Component({
    selector: 'tum-ui-item-title',
    template: '<ng-content />',
    host: {
        class: 'tum-ui-item-title tum:m-0 tum:truncate tum:font-medium tum:text-text',
        '[attr.data-slot]': '"item-title"',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiItemTitleComponent {}

/** The row's secondary line: metadata, a timestamp, a path. Truncates rather than wrapping the row taller. */
@Component({
    selector: 'tum-ui-item-description',
    template: '<ng-content />',
    host: {
        class: 'tum-ui-item-description tum:m-0 tum:truncate tum:text-sm tum:text-muted',
        '[attr.data-slot]': '"item-description"',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiItemDescriptionComponent {}

/** Trailing controls and badges. It never shrinks, so a long title truncates before an action disappears. */
@Component({
    selector: 'tum-ui-item-actions',
    template: '<ng-content />',
    host: {
        class: 'tum-ui-item-actions tum:flex tum:shrink-0 tum:items-center tum:gap-2',
        '[attr.data-slot]': '"item-actions"',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiItemActionsComponent {}
