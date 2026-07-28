import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

@Component({
    selector: 'tum-ui-card',
    templateUrl: './tum-ui-card.component.html',
    host: {
        '[class]': 'hostClasses()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiCardComponent {
    readonly header = input<string>();

    readonly subheader = input<string>();

    readonly styleClass = input<string>('');

    protected readonly hostClasses = computed(() =>
        `tum-ui-card flex flex-col rounded-xl shadow-sm bg-tum-ui-surface-0 dark:bg-tum-ui-surface-900 text-tum-ui-text ${this.styleClass()}`.trim(),
    );
}
