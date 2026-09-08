import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IconProp } from '@fortawesome/fontawesome-svg-core';

export type TumUiIconFieldPosition = 'left' | 'right';

@Component({
    selector: 'tum-ui-icon-field',
    templateUrl: './tum-ui-icon-field.component.html',
    styleUrl: './tum-ui-icon-field.component.scss',
    imports: [FaIconComponent],
    host: {
        '[attr.data-slot]': '"icon-field"',
        class: 'tum-ui-icon-field',
        '[attr.data-position]': 'iconPosition()',
        '[attr.data-has-icon]': 'icon() ? "" : null',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiIconFieldComponent {
    readonly icon = input<IconProp>();
    readonly iconPosition = input<TumUiIconFieldPosition>('left');
}
