import { Component, input } from '@angular/core';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCheck, faExclamationTriangle, faInfoCircle } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiTagComponent } from '@tumaet/ui-angular';

/**
 * Status indicator for student exams
 * Number of student exams should match the number of registered users
 */
@Component({
    selector: 'jhi-student-exam-status',
    templateUrl: './student-exam-status.component.html',
    imports: [TranslateDirective, ArtemisTranslatePipe, FaIconComponent, TumAetUiTagComponent],
})
export class StudentExamStatusComponent {
    hasStudentsWithoutExam = input.required<boolean>();
    isTestExam = input.required<boolean>();

    protected readonly faInfoCircle = faInfoCircle;
    protected readonly faExclamationTriangle = faExclamationTriangle;
    protected readonly faCheck = faCheck;
}
