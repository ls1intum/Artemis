import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import type { IconProp } from '@fortawesome/fontawesome-svg-core';
import type { TumUiEmptyStateVariant } from './tum-ui-empty-state.variants';

/**
 * Presents a consistent empty state for views without content, including empty search results, while leaving
 * actions and documentation links to the host application. Both content slots are optional. Project each action
 * with `[tumUiEmptyStateActions]` and a documentation link with `[tumUiEmptyStateDocumentation]`.
 */
@Component({
    selector: 'tum-ui-empty-state',
    templateUrl: './tum-ui-empty-state.component.html',
    styleUrl: './tum-ui-empty-state.component.scss',
    imports: [FaIconComponent],
    host: {
        class: 'tum-ui-empty-state tum:flex tum:flex-col tum:items-center tum:text-center',
        '[attr.data-variant]': 'variant()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiEmptyStateComponent {
    /** Decorative icon shown above the empty state content. */
    readonly icon = input.required<IconProp>();

    /** Visual treatment of the decorative icon container. */
    readonly variant = input<TumUiEmptyStateVariant>('outlined');

    /** Heading that describes what content is missing. */
    readonly title = input.required<string>();

    /** Optional explanation shown below the heading. */
    readonly description = input<string>();
}
