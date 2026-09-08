import { ChangeDetectionStrategy, Component, ViewEncapsulation, input } from '@angular/core';

/** Block rhythm of the projected document. `compact` is for prose inside a card body or a panel. */
export type TumUiProseDensity = 'comfortable' | 'compact';

/**
 * Applies document typography to projected or rendered HTML. The consumer is responsible for sanitization.
 *
 * Bind `[innerHTML]` directly to this element: block spacing uses direct-child selectors, so an intervening
 * wrapper loses that spacing. The component does not change heading levels or add markup.
 *
 * ```html
 * <tum-ui-prose density="compact" [innerHTML]="renderedMarkdown()"></tum-ui-prose>
 * <article tumUiProse [innerHTML]="renderedMarkdown()"></article>
 * ```
 *
 * `ViewEncapsulation.None` lets styles reach `[innerHTML]` content. All rules are scoped under `.tum-ui-prose`;
 * keeping that class outside `:where()` gives them precedence over bare host-page heading rules.
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
