import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
    selector: 'tum-ui-card',
    templateUrl: './tum-ui-card.component.html',
    host: {
        class: 'tum-ui-card tum:flex tum:flex-col tum:rounded-xl tum:shadow-sm tum:bg-overlay-background tum:text-text',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiCardComponent {
    readonly header = input<string>();

    readonly subheader = input<string>();
}
