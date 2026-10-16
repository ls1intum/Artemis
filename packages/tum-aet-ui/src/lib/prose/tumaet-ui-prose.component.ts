import { ChangeDetectionStrategy, Component, ViewEncapsulation, input } from '@angular/core';

/** Block rhythm of the projected document. `compact` is for prose inside a card body or a panel. */
export type TumAetUiProseDensity = 'comfortable' | 'compact';

/**
 * Applies document typography to projected or rendered HTML. The consumer is responsible for sanitization.
 *
 * Bind `[innerHTML]` directly to this element: block spacing uses direct-child selectors, so an intervening
 * wrapper loses that spacing. The component does not change heading levels or add markup.
 *
 * ```html
 * <tumaet-ui-prose density="compact" [innerHTML]="renderedMarkdown()"></tumaet-ui-prose>
 * <article tumAetUiProse [innerHTML]="renderedMarkdown()"></article>
 * ```
 *
 * `ViewEncapsulation.None` lets styles reach `[innerHTML]` content. All rules are scoped under `.tumaet-ui-prose`;
 * keeping that class outside `:where()` gives them precedence over bare host-page heading rules.
 */
@Component({
    selector: 'tumaet-ui-prose, [tumAetUiProse]',
    template: '<ng-content />',
    styleUrl: './tumaet-ui-prose.component.scss',
    encapsulation: ViewEncapsulation.None,
    host: {
        class: 'tumaet-ui-prose tumaet:block tumaet:text-text',
        '[attr.data-slot]': '"prose"',
        '[attr.data-density]': 'density()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiProseComponent {
    /** Block rhythm. `compact` tightens the spacing between blocks for prose inside a panel or a card body. */
    readonly density = input<TumAetUiProseDensity>('comfortable');
}
