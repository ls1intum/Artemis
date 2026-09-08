import { ChangeDetectionStrategy, Component, computed, effect, input } from '@angular/core';
import { TumUiSize, TumUiSizeAlias, resolveSize, warnDeprecatedInput } from '../foundation/tum-ui-vocabulary';

/** Surface treatment of a card. */
export type TumUiCardVariant = 'elevated' | 'outline' | 'flat';

const CARD_BASE = 'tum-ui-card tum:flex tum:min-w-0 tum:flex-col tum:rounded-xl tum:text-text';

const CARD_VARIANT: Record<TumUiCardVariant, string> = {
    // The default: lifted off the page by a shadow rather than boxed in by a rule.
    elevated: 'tum:bg-overlay-background tum:shadow-sm',
    // For a card among cards, where four shadows would read as four equal claims.
    outline: 'tum:bg-content-background tum:border tum:border-border',
    // For a card inside another container, which already drew the boundary.
    flat: 'tum:bg-content-background',
};

/**
 * A bounded region with its own title, body and actions.
 *
 * Compose it from `tum-ui-card-header` (holding `-title`, `-description` and `-action`), `tum-ui-card-content` and
 * `tum-ui-card-footer`. Give `tum-ui-card-title` a `level` so it renders a real heading — a card is a section, and
 * a section whose title is a `<div>` is invisible to heading navigation.
 *
 * ```html
 * <tum-ui-card>
 *     <tum-ui-card-header>
 *         <tum-ui-card-title [level]="2">Progress</tum-ui-card-title>
 *         <tum-ui-card-description>Five stages, in order.</tum-ui-card-description>
 *         <tum-ui-card-action><tum-ui-tag variant="quiet">Step 2 of 5</tum-ui-tag></tum-ui-card-action>
 *     </tum-ui-card-header>
 *     <tum-ui-card-content>…</tum-ui-card-content>
 * </tum-ui-card>
 * ```
 *
 * Override the internal rhythm with `--tum-ui-card-spacing` on the host; class overrides do not work here, because
 * a consumer class and a host class merge additively and the winner is stylesheet order, which the consumer does
 * not control.
 *
 * @remarks
 * `header`, `subheader`, `[tumUiCardHeader]` and `[tumUiCardFooter]` are deprecated. They still render, so no call
 * site breaks, but `[tumUiCardHeader]` projects outside the padded body while `[tumUiCardFooter]` projects inside
 * it — an inconsistency the sub-components exist to end.
 */
@Component({
    selector: 'tum-ui-card',
    templateUrl: './tum-ui-card.component.html',
    styleUrl: './tum-ui-card.component.scss',
    host: {
        '[class]': 'hostClasses()',
        '[attr.data-slot]': '"card"',
        '[attr.data-size]': 'effectiveSize()',
        '[attr.data-variant]': 'variant()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiCardComponent {
    /** Internal spacing scale. */
    readonly size = input<TumUiSize | TumUiSizeAlias>('medium');

    /** Surface treatment. */
    readonly variant = input<TumUiCardVariant>('elevated');

    /**
     * @deprecated Project `<tum-ui-card-header><tum-ui-card-title [level]="2">…` instead. A string title cannot be
     * a heading, so every card using it is invisible to heading navigation.
     */
    readonly header = input<string>();

    /** @deprecated Project `<tum-ui-card-description>` inside `<tum-ui-card-header>` instead. */
    readonly subheader = input<string>();

    protected readonly effectiveSize = computed(() => resolveSize(this.size(), 'tum-ui-card'));

    protected readonly hostClasses = computed(() => `${CARD_BASE} ${CARD_VARIANT[this.variant()]}`);

    /* eslint-disable @typescript-eslint/no-deprecated -- this is the shim that keeps the deprecated inputs working. */
    protected readonly showLegacyCaption = computed(() => Boolean(this.header()?.trim() || this.subheader()?.trim()));

    constructor() {
        // Reported from an effect rather than from the computed the template reads, so the warning is a side effect
        // of the input being used at all and never of a re-render.
        effect(() => {
            if (this.header() !== undefined) {
                warnDeprecatedInput('tum-ui-card', 'header', 'a projected <tum-ui-card-header> with a <tum-ui-card-title [level]>');
            }
            if (this.subheader() !== undefined) {
                warnDeprecatedInput('tum-ui-card', 'subheader', 'a projected <tum-ui-card-description>');
            }
        });
    }
    /* eslint-enable @typescript-eslint/no-deprecated */
}
