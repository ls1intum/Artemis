import { ChangeDetectionStrategy, Component, ViewEncapsulation, input } from '@angular/core';

/** Block rhythm of the projected document. `compact` is for prose inside a card body or a panel. */
export type TumUiProseDensity = 'comfortable' | 'compact';

/**
 * Styles a subtree of author-generated HTML with the design system's typography.
 *
 * Use it around anything whose markup the application did not write element by element: a rendered Markdown
 * document, a rich-text field, a stored description. It only ever styles — it injects no markup, sets no heading
 * levels, and starts its scale below the page's own `h1`, so the document a consumer projects keeps whatever
 * outline it arrived with.
 *
 * **Sanitisation stays with the consumer.** A design system must not own an application's Markdown pipeline, so
 * this component takes no HTML input of its own: render the string yourself and project the result.
 *
 * ```html
 * <tum-ui-prose density="compact" [innerHTML]="renderedMarkdown()"></tum-ui-prose>
 * <article tumUiProse [innerHTML]="renderedMarkdown()"></article>
 * ```
 *
 * Bind the HTML to *this* element, not to a wrapper inside it. The vertical rhythm is set by direct-child rules
 * (`.tum-ui-prose > * + *`), so a `<div>` between this element and the document makes every block a grandchild and
 * silently drops the spacing between headings, paragraphs and lists. The descendant rules still apply, so the result
 * looks styled rather than broken, which is what makes the mistake easy to miss.
 *
 * Two implementation notes that are load-bearing and must survive a cleanup:
 *
 * 1. `ViewEncapsulation.None` is required. Content assigned through `[innerHTML]` never receives an `_ngcontent`
 *    attribute, so emulated encapsulation cannot reach a single element of it. Every rule in the stylesheet is
 *    therefore nested under `.tum-ui-prose` by hand.
 * 2. The rules deliberately do **not** use `:where()`. `.tum-ui-prose h2` has specificity (0,1,1); a host page's
 *    bare `h2` rule has (0,0,1), and in the same unlayered origin the class selector wins on specificity alone.
 *    Wrapping the selectors in `:where()` would drop them to (0,0,1) and hand the win back to the host page,
 *    which is exactly the problem this component exists to remove — without a single `!important`.
 */
@Component({
    selector: 'tum-ui-prose, [tumUiProse]',
    template: '<ng-content />',
    styleUrl: './tum-ui-prose.component.scss',
    encapsulation: ViewEncapsulation.None,
    host: {
        class: 'tum-ui-prose tum:block tum:text-text',
        '[attr.data-slot]': '"prose"',
        '[attr.data-density]': 'density()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiProseComponent {
    /** Block rhythm. `compact` tightens the spacing between blocks for prose inside a panel or a card body. */
    readonly density = input<TumUiProseDensity>('comfortable');
}
