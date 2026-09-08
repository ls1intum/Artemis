import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** Presentation of the placeholder's leading graphic. */
export type TumUiEmptyMediaVariant = 'default' | 'icon';

const MEDIA_BASE = 'tum-ui-empty-media tum:flex tum:items-center tum:justify-center tum:text-muted';

const MEDIA_VARIANT: Record<TumUiEmptyMediaVariant, string> = {
    // Bare artwork: an illustration or an image brings its own frame, so the slot only centres it.
    default: '',
    // A single glyph needs a shape around it, or it reads as a stray character rather than a placeholder.
    icon: 'tum:size-12 tum:rounded-xl tum:bg-hover-background tum:text-xl',
};

/**
 * Groups the media, title and description of a {@link TumUiEmptyComponent} so the action below them is separated
 * from the explanation above them by a single gap rather than by four equal ones.
 */
@Component({
    selector: 'tum-ui-empty-header',
    template: '<ng-content />',
    host: {
        class: 'tum-ui-empty-header tum:flex tum:flex-col tum:items-center tum:gap-2',
        '[attr.data-slot]': '"empty-header"',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiEmptyHeaderComponent {}

/**
 * Leading graphic of an empty state.
 *
 * It is `aria-hidden`: the graphic restates what the title already says, and an unlabelled decorative glyph
 * announced before the explanation is noise.
 */
@Component({
    selector: 'tum-ui-empty-media',
    template: '<ng-content />',
    host: {
        '[class]': 'hostClasses()',
        'aria-hidden': 'true',
        '[attr.data-slot]': '"empty-media"',
        '[attr.data-variant]': 'variant()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiEmptyMediaComponent {
    /** `icon` frames a single glyph in a tinted square; `default` leaves an illustration to bring its own frame. */
    readonly variant = input<TumUiEmptyMediaVariant>('default');

    protected readonly hostClasses = computed(() => `${MEDIA_BASE} ${MEDIA_VARIANT[this.variant()]}`.trimEnd());
}

/**
 * The sentence that names what is missing.
 *
 * It renders as emphasised body text and **not** as a heading: an empty state usually replaces the content of a
 * section that already has one, and a second heading at an arbitrary level would corrupt the page outline. Wrap it
 * in your own `<h*>` where the empty state genuinely opens a new section.
 */
@Component({
    selector: 'tum-ui-empty-title',
    template: '<ng-content />',
    host: {
        class: 'tum-ui-empty-title tum:m-0 tum:text-base tum:font-semibold tum:text-text tum:text-balance',
        '[attr.data-slot]': '"empty-title"',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiEmptyTitleComponent {}

/** Supporting sentence: what would be here, or who can put something here. */
@Component({
    selector: 'tum-ui-empty-description',
    template: '<ng-content />',
    host: {
        class: 'tum-ui-empty-description tum:m-0 tum:max-w-prose tum:text-sm tum:text-muted tum:text-balance',
        '[attr.data-slot]': '"empty-description"',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiEmptyDescriptionComponent {}

/** Everything a reader can act on: the control that resolves the emptiness, or a link to whoever can. */
@Component({
    selector: 'tum-ui-empty-content',
    template: '<ng-content />',
    host: {
        class: 'tum-ui-empty-content tum:flex tum:flex-wrap tum:items-center tum:justify-center tum:gap-2 tum:text-sm',
        '[attr.data-slot]': '"empty-content"',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiEmptyContentComponent {}
