import { Component, Input } from '@angular/core';
import { faCheckCircle, faExclamationTriangle, faInfoCircle } from '@fortawesome/free-solid-svg-icons';

/**
 * Status indicator for student exams
 * Number of student exams should match the number of registered users
 */
@Component({
    selector: 'jhi-student-exam-status',
    templateUrl: `./student-exam-status.component.html`,
})
export class StudentExamStatusComponent {
    @Input() hasStudentsWithoutExam: boolean;
    @Input() isTestExam: boolean;

    // Icons
    faExclamationTriangle = faExclamationTriangle;
    faCheckCircle = faCheckCircle;
    faInfoCircle = faInfoCircle;
}
