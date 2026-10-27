import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TumAetUiCardVariant, tumAetUiCardClasses } from './tumaet-ui-card.variants';

@Component({
    selector: 'tumaet-ui-card',
    templateUrl: './tumaet-ui-card.component.html',
    host: {
        '[class]': 'hostClasses()',
        '[attr.data-variant]': 'variant()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiCardComponent {
    readonly header = input<string>();

    readonly subheader = input<string>();

    /** Surface treatment: `elevated` (default) on the overlay background with a shadow, `muted` on the neutral fill with a border. */
    readonly variant = input<TumAetUiCardVariant>('elevated');

    protected readonly hostClasses = computed(() => tumAetUiCardClasses(this.variant()));
}
