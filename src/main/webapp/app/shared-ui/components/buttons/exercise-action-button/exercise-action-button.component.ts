import { Component, computed, input, model } from '@angular/core';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faCircleNotch } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TumAetUiButtonSeverity, tumAetUiButtonClasses } from '@tumaet/ui-angular';

@Component({
    selector: 'button[jhi-exercise-action-button]',
    templateUrl: './exercise-action-button.component.html',
    imports: [FaIconComponent],
    host: {
        '[class]': 'buttonClasses()',
        '[attr.aria-busy]': 'buttonLoading() ? true : null',
        '[disabled]': 'isDisabled()',
    },
})
export class ExerciseActionButtonComponent {
    // Inputs
    buttonIcon = input<IconProp | undefined>(undefined);
    buttonLabel = input<string | undefined>(undefined);
    hideLabelMobile = input(true);
    hideLabelBreakpoint = input<'md' | 'xl'>('md');
    forceDisabled = input(false);
    buttonLoading = input(false);
    outlined = input(false);
    smallButton = input(false);

    /** Explicit action intent takes precedence over the legacy primary/secondary toggle. */
    severity = input<TumAetUiButtonSeverity>();

    /** Allows parent to override primary/secondary styling programmatically. */
    overrideSecondary = model<boolean | undefined>(undefined);

    protected readonly buttonClasses = computed(() =>
        tumAetUiButtonClasses({
            severity: this.severity() ?? (this.overrideSecondary() ? 'secondary' : 'primary'),
            variant: this.outlined() ? 'outlined' : 'solid',
            size: this.smallButton() ? 'small' : 'default',
        }),
    );

    protected isDisabled = computed(() => this.buttonLoading() || this.forceDisabled());

    // Icons
    faCircleNotch = faCircleNotch;
}
