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
        `tum-ui-card tum:flex tum:flex-col tum:rounded-xl tum:shadow-sm tum:bg-tum-ui-content-background tum:text-tum-ui-text ${this.styleClass()}`.trim(),
    );
}
