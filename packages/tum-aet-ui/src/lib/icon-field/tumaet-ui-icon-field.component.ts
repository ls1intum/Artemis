import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IconProp } from '@fortawesome/fontawesome-svg-core';

export type TumAetUiIconFieldPosition = 'left' | 'right';

@Component({
    selector: 'tumaet-ui-icon-field',
    templateUrl: './tumaet-ui-icon-field.component.html',
    styleUrl: './tumaet-ui-icon-field.component.scss',
    imports: [FaIconComponent],
    host: {
        class: 'tumaet-ui-icon-field',
        '[attr.data-position]': 'iconPosition()',
        '[attr.data-has-icon]': 'icon() ? "" : null',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiIconFieldComponent {
    readonly icon = input<IconProp>();
    readonly iconPosition = input<TumAetUiIconFieldPosition>('left');
}
