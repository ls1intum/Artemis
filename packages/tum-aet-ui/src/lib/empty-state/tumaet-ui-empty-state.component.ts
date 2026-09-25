import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import type { IconProp } from '@fortawesome/fontawesome-svg-core';
import type { TumAetUiEmptyStateVariant } from './tumaet-ui-empty-state.variants';

/**
 * Presents a consistent empty state for views without content, including empty search results, while leaving
 * actions and documentation links to the host application. Both content slots are optional. Project each action
 * with `[tumAetUiEmptyStateActions]` and a documentation link with `[tumAetUiEmptyStateDocumentation]`.
 */
@Component({
    selector: 'tumaet-ui-empty-state',
    templateUrl: './tumaet-ui-empty-state.component.html',
    styleUrl: './tumaet-ui-empty-state.component.scss',
    imports: [FaIconComponent],
    host: {
        class: 'tumaet-ui-empty-state tumaet:flex tumaet:flex-col tumaet:items-center tumaet:text-center',
        '[attr.data-variant]': 'variant()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiEmptyStateComponent {
    /** Decorative icon shown above the empty state content. */
    readonly icon = input.required<IconProp>();

    /** Visual treatment of the decorative icon container. */
    readonly variant = input<TumAetUiEmptyStateVariant>('outlined');

    /** Heading that describes what content is missing. */
    readonly title = input.required<string>();

    /** Optional explanation shown below the heading. */
    readonly description = input<string>();
}
