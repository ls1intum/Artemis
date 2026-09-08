import { ChangeDetectionStrategy, Component, booleanAttribute, computed, input } from '@angular/core';
import { TumUiSeverity, TumUiSeverityAlias, TumUiSize, TumUiSizeAlias, resolveSeverity, resolveSize } from '../foundation/tum-ui-vocabulary';

/** Severities a tag can carry. A subset of {@link TumUiSeverity}: a tag has no `primary` role. */
export type TumUiTagSeverity = Extract<TumUiSeverity, 'secondary' | 'success' | 'info' | 'warning' | 'danger' | 'contrast'>;

/** How loudly the tag states itself. */
export type TumUiTagVariant = 'solid' | 'quiet';

const TAG_BASE = 'tum:inline-flex tum:items-center tum:gap-1';

const TAG_SIZE: Record<TumUiSize, string> = {
    small: 'tum:px-1.5 tum:py-0.5 tum:text-xs',
    medium: 'tum:px-2 tum:py-1 tum:text-sm',
    large: 'tum:px-2.5 tum:py-1.5 tum:text-base',
};

// `quiet` exists because a badge as loud as the label it annotates competes with the thing it is describing rather
// than describing it. The colour is identical; only the weight of the statement changes.
const TAG_VARIANT: Record<TumUiTagVariant, string> = {
    solid: 'tum:font-bold',
    quiet: 'tum:font-medium',
};

const TAG_SEVERITY: Record<TumUiTagSeverity, string> = {
    secondary: 'tum:bg-hover-background tum:text-text',
    // The four semantic fills are mixed from state colours in SCSS, keyed off `data-severity`, because
    // `color-mix()` against the surface behind them is not expressible as a utility class.
    success: '',
    info: '',
    warning: '',
    danger: '',
    contrast: 'tum:bg-contrast-background tum:text-contrast',
};

/**
 * A short, static label attached to something else: a state, a category, a count.
 *
 * A tag is not a control and not a status message — it neither responds to a click nor announces itself. Give the
 * text through `value` or by projection; both render, in that order, so an icon can sit beside a translated word.
 *
 * The severity is a colour role and never the only signal: a colour-blind reader and a Windows High Contrast
 * reader both get the word, so write the state into the tag rather than relying on the fill to say it.
 */
@Component({
    selector: 'tum-ui-tag',
    templateUrl: './tum-ui-tag.component.html',
    styleUrl: './tum-ui-tag.component.scss',
    host: {
        class: 'tum-ui-tag',
        '[attr.data-slot]': '"tag"',
        '[attr.data-severity]': 'effectiveSeverity()',
        '[attr.data-variant]': 'variant()',
        '[attr.data-size]': 'effectiveSize()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiTagComponent {
    /** Colour role. `warn` is accepted as a deprecated spelling of `warning`. */
    readonly severity = input<TumUiTagSeverity | TumUiSeverityAlias>('secondary');

    /** Label text. It renders alongside projected content rather than replacing it. */
    readonly value = input<string>();

    /** Size step. Match it to the control the tag sits beside. */
    readonly size = input<TumUiSize | TumUiSizeAlias>('medium');

    /** `quiet` keeps the colour and drops the weight, for a badge that annotates rather than announces. */
    readonly variant = input<TumUiTagVariant>('solid');

    /** Draws the tag as a pill rather than a rounded rectangle. */
    readonly rounded = input(false, { transform: booleanAttribute });

    protected readonly effectiveSeverity = computed(() => resolveSeverity<TumUiTagSeverity>(this.severity(), 'tum-ui-tag'));
    protected readonly effectiveSize = computed(() => resolveSize(this.size(), 'tum-ui-tag'));

    protected readonly tagClasses = computed(() =>
        `${TAG_BASE} ${TAG_SIZE[this.effectiveSize()]} ${TAG_VARIANT[this.variant()]} ${this.rounded() ? 'tum:rounded-full' : 'tum:rounded-md'} ${TAG_SEVERITY[this.effectiveSeverity()]}`
            .replace(/\s+/g, ' ')
            .trim(),
    );
}
