import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
    selector: 'tumaet-ui-card',
    templateUrl: './tumaet-ui-card.component.html',
    host: {
        class: 'tumaet-ui-card tumaet:flex tumaet:flex-col tumaet:rounded-xl tumaet:shadow-sm tumaet:bg-overlay-background tumaet:text-text',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiCardComponent {
    readonly header = input<string>();

    readonly subheader = input<string>();
}
