import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Heading level a card title renders at, or `undefined` for a title that is not a section heading. */
export type TumUiCardTitleLevel = 1 | 2 | 3 | 4 | 5 | 6;

/**
 * Title row of a card: the title and its description on the left, one action on the right.
 *
 * The action slot is the reason this is a component rather than a `<div>`. "Title left, control right" is the most
 * common card header there is, and without a place to put the control the only way to build it was to abandon the
 * card and hand-roll the whole header.
 */
@Component({
    selector: 'tum-ui-card-header',
    templateUrl: './tum-ui-card-header.component.html',
    host: {
        class: 'tum-ui-card-header tum:flex tum:items-start tum:justify-between tum:gap-4',
        '[attr.data-slot]': '"card-header"',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiCardHeaderComponent {}

/**
 * The card's title.
 *
 * Give it a `level` and it joins the page's heading outline, so a screen-reader user can jump between the regions
 * of a page instead of reading through them. Omit `level` only where the card genuinely is not a section — a
 * metric tile in a grid, for instance.
 *
 * The heading is `role="heading"` with `aria-level` rather than a real `<h*>`, for two reasons. The level is an
 * input, and a `@switch` over six native tags would need six `<ng-content>` outlets for one slot, which projects
 * once and cannot be moved between branches. And the typography then comes from this component's own class rather
 * than from an element selector a host application's unlayered `h2` rule could outrank — which is how a card
 * title gets its size without a single `!important`.
 */
@Component({
    selector: 'tum-ui-card-title',
    template: '<ng-content />',
    host: {
        class: 'tum-ui-card-title tum:block tum:m-0 tum:text-lg tum:font-semibold tum:text-text tum:text-balance',
        '[attr.role]': "level() === undefined ? null : 'heading'",
        '[attr.aria-level]': 'level() ?? null',
        '[attr.data-slot]': '"card-title"',
        '[attr.data-level]': 'level() ?? null',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiCardTitleComponent {
    /** Heading level. Omit it for a title that is not a section heading; it then renders as plain emphasised text. */
    readonly level = input<TumUiCardTitleLevel>();
}

/** One sentence under the title saying what the card is for. */
@Component({
    selector: 'tum-ui-card-description',
    template: '<ng-content />',
    host: {
        class: 'tum-ui-card-description tum:block tum:m-0 tum:text-sm tum:text-muted',
        '[attr.data-slot]': '"card-description"',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiCardDescriptionComponent {}

/** The card's header-level control: a button, a menu trigger, a status tag. */
@Component({
    selector: 'tum-ui-card-action',
    template: '<ng-content />',
    host: {
        class: 'tum-ui-card-action tum:flex tum:shrink-0 tum:items-center tum:gap-2',
        '[attr.data-slot]': '"card-action"',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiCardActionComponent {}

/** The card's body. */
@Component({
    selector: 'tum-ui-card-content',
    template: '<ng-content />',
    host: {
        class: 'tum-ui-card-content tum:block tum:min-w-0',
        '[attr.data-slot]': '"card-content"',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiCardContentComponent {}

/** Closing row: secondary actions, a timestamp, a footnote. */
@Component({
    selector: 'tum-ui-card-footer',
    template: '<ng-content />',
    host: {
        class: 'tum-ui-card-footer tum:flex tum:flex-wrap tum:items-center tum:gap-2 tum:text-sm tum:text-muted',
        '[attr.data-slot]': '"card-footer"',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiCardFooterComponent {}
