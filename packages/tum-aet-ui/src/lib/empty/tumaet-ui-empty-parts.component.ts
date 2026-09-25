import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** Presentation of the placeholder's leading graphic. */
export type TumAetUiEmptyMediaVariant = 'default' | 'icon';

const MEDIA_BASE = 'tumaet-ui-empty-media tumaet:flex tumaet:items-center tumaet:justify-center tumaet:text-muted';

const MEDIA_VARIANT: Record<TumAetUiEmptyMediaVariant, string> = {
    // Bare artwork: an illustration or an image brings its own frame, so the slot only centres it.
    default: '',
    // A single glyph needs a shape around it, or it reads as a stray character rather than a placeholder.
    icon: 'tumaet:size-12 tumaet:rounded-xl tumaet:bg-hover-background tumaet:text-xl',
};

/**
 * Groups the media, title and description of a {@link TumAetUiEmptyComponent} so the action below them is separated
 * from the explanation above them by a single gap rather than by four equal ones.
 */
@Component({
    selector: 'tumaet-ui-empty-header',
    template: '<ng-content />',
    host: {
        class: 'tumaet-ui-empty-header tumaet:flex tumaet:flex-col tumaet:items-center tumaet:gap-2',
        '[attr.data-slot]': '"empty-header"',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiEmptyHeaderComponent {}

/**
 * Leading graphic of an empty state.
 *
 * It is `aria-hidden`: the graphic restates what the title already says, and an unlabelled decorative glyph
 * announced before the explanation is noise.
 */
@Component({
    selector: 'tumaet-ui-empty-media',
    template: '<ng-content />',
    host: {
        '[class]': 'hostClasses()',
        'aria-hidden': 'true',
        '[attr.data-slot]': '"empty-media"',
        '[attr.data-variant]': 'variant()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiEmptyMediaComponent {
    /** `icon` frames a single glyph in a tinted square; `default` leaves an illustration to bring its own frame. */
    readonly variant = input<TumAetUiEmptyMediaVariant>('default');

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
    selector: 'tumaet-ui-empty-title',
    template: '<ng-content />',
    host: {
        class: 'tumaet-ui-empty-title tumaet:m-0 tumaet:text-base tumaet:font-semibold tumaet:text-text tumaet:text-balance',
        '[attr.data-slot]': '"empty-title"',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiEmptyTitleComponent {}

/** Supporting sentence: what would be here, or who can put something here. */
@Component({
    selector: 'tumaet-ui-empty-description',
    template: '<ng-content />',
    host: {
        class: 'tumaet-ui-empty-description tumaet:m-0 tumaet:max-w-prose tumaet:text-sm tumaet:text-muted tumaet:text-balance',
        '[attr.data-slot]': '"empty-description"',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiEmptyDescriptionComponent {}

/** Everything a reader can act on: the control that resolves the emptiness, or a link to whoever can. */
@Component({
    selector: 'tumaet-ui-empty-content',
    template: '<ng-content />',
    host: {
        class: 'tumaet-ui-empty-content tumaet:flex tumaet:flex-wrap tumaet:items-center tumaet:justify-center tumaet:gap-2 tumaet:text-sm',
        '[attr.data-slot]': '"empty-content"',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiEmptyContentComponent {}
