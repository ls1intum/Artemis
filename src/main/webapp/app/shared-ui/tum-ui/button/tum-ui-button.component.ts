import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { TumUiButtonSeverity, TumUiButtonSize, tumUiButtonClasses } from 'app/shared-ui/tum-ui/button/tum-ui-button.variants';

/**
 * Owned Artemis button, part of the tum-aet-ui kit (future @tumaet/ui-angular).
 *
 * Signal-based element component that renders a native `<button>` styled with Artemis token
 * utilities to match the current PrimeNG Aura look. No PrimeNG / Bootstrap / Angular CDK dependency.
 * The API intentionally mirrors TumApply's button atom (severity / size / outlined) so the two apps
 * can converge on one shared component later.
 */
@Component({
    selector: 'tum-ui-button',
    templateUrl: './tum-ui-button.component.html',
    styleUrl: './tum-ui-button.component.scss',
    imports: [FaIconComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiButtonComponent {
    readonly severity = input<TumUiButtonSeverity>('primary');
    readonly size = input<TumUiButtonSize>('default');
    readonly outlined = input(false);
    readonly text = input(false);
    readonly disabled = input(false);
    readonly icon = input<IconProp | undefined>(undefined);
    readonly type = input<'button' | 'submit'>('button');

    readonly clicked = output<MouseEvent>();

    protected readonly buttonClasses = computed(() => tumUiButtonClasses({ severity: this.severity(), size: this.size(), outlined: this.outlined(), text: this.text() }));

    protected onClick(event: MouseEvent): void {
        this.clicked.emit(event);
    }
}
