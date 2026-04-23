import { Component, computed, input, model } from '@angular/core';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faCircleNotch } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgClass } from '@angular/common';

@Component({
    selector: 'button[jhi-exercise-action-button]',
    templateUrl: './exercise-action-button.component.html',
    styleUrls: ['../../../../core/course/overview/course-overview/course-overview.scss'],
    imports: [FaIconComponent, NgClass],
    host: {
        '[class.btn]': 'true',
        '[class.btn-outline-primary]': 'outlined()',
        '[class.btn-sm]': 'smallButton()',
        '[class.btn-primary]': 'isPrimary()',
        '[class.btn-secondary]': 'isSecondary()',
        '[disabled]': 'isDisabled()',
    },
})
export class ExerciseActionButtonComponent {
    // Inputs
    buttonIcon = input<IconProp | undefined>(undefined);
    buttonLabel = input<string | undefined>(undefined);
    hideLabelMobile = input(true);
    overwriteDisabled = input(false);
    buttonLoading = input(false);
    outlined = input(false);
    smallButton = input(false);

    /** Allows parent to override primary/secondary styling programmatically. */
    overrideSecondary = model<boolean | undefined>(undefined);

    protected isPrimary = computed(() => !this.outlined() && !(this.overrideSecondary() ?? false));
    protected isSecondary = computed(() => !this.outlined() && (this.overrideSecondary() ?? false));

    protected isDisabled = computed(() => this.buttonLoading() || this.overwriteDisabled());

    // Icons
    faCircleNotch = faCircleNotch;
}
