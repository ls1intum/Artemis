import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

@Component({
    selector: 'tum-ui-button-group',
    template: '<ng-content />',
    styleUrl: './tum-ui-button-group.component.scss',
    host: {
        '[class]': 'hostClasses()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiButtonGroupComponent {
    readonly styleClass = input<string>('');

    protected readonly hostClasses = computed(() => `tum-ui-button-group ${this.styleClass()}`.trim());
}
