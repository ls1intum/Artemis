import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { faBan, faCheck, faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';
import { DynamicDialogRef } from 'primeng/dynamicdialog';
import { ButtonModule } from 'primeng/button';

import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

@Component({
    selector: 'jhi-iris-in-class-quiz-start-warning',
    templateUrl: './iris-in-class-quiz-start-warning.component.html',
    styleUrls: ['../../../../exercise/exercise-update-warning/exercise-update-warning.component.scss'],
    imports: [TranslateDirective, FaIconComponent, ButtonModule],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
/**
 * Confirmation dialog shown before starting an in-class quiz for an exercise whose due
 * date has not yet passed, warning the instructor about the effect on students still working.
 */
export class IrisInClassQuizStartWarningComponent {
    private readonly dialogRef = inject(DynamicDialogRef);

    protected readonly faBan = faBan;
    protected readonly faCheck = faCheck;
    protected readonly faExclamationTriangle = faExclamationTriangle;

    cancel(): void {
        this.dialogRef.close(false);
    }

    startQuiz(): void {
        this.dialogRef.close(true);
    }
}
