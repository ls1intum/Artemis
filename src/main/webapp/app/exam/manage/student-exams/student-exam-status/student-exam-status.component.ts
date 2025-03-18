import { Component, input } from '@angular/core';
import { faCheckCircle, faExclamationTriangle, faInfoCircle } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

/**
 * Status indicator for student exams
 * Number of student exams should match the number of registered users
 */
@Component({
    selector: 'jhi-student-exam-status',
    templateUrl: `./student-exam-status.component.html`,
    imports: [FaIconComponent, TranslateDirective, NgbTooltip, ArtemisTranslatePipe],
})
export class StudentExamStatusComponent {
    hasStudentsWithoutExam = input.required<boolean>();
    isTestExam = input.required<boolean>();

    // Icons
    faExclamationTriangle = faExclamationTriangle;
    faCheckCircle = faCheckCircle;
    faInfoCircle = faInfoCircle;
}
