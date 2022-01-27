import { Component, Input } from '@angular/core';
import { faCheckCircle, faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';

/**
 * Status indicator for student exams
 * Number of student exams should match the number of registered users
 */
@Component({
    selector: 'jhi-student-exam-status',
    template: `
        <div class="d-flex mt-2 mb-3">
            <div *ngIf="hasStudentsWithoutExam; else allStudentsHaveExams" class="d-flex badge bg-warning">
                <fa-icon
                    class="ms-2 text-white"
                    [icon]="faExclamationTriangle"
                    [ngbTooltip]="'artemisApp.studentExams.studentExamStatusWarningTooltip' | artemisTranslate"
                ></fa-icon>
                <span class="ms-1" jhiTranslate="artemisApp.studentExams.studentExamStatusWarning"></span>
            </div>
            <ng-template #allStudentsHaveExams>
                <div class="d-flex badge bg-success">
                    <fa-icon class="ms-2 text-white" [icon]="faCheckCircle"></fa-icon>
                    <span class="ms-1" jhiTranslate="artemisApp.studentExams.studentExamStatusSuccess"></span>
                </div>
            </ng-template>
        </div>
    `,
})
export class StudentExamStatusComponent {
    @Input() hasStudentsWithoutExam: boolean;

    // Icons
    faExclamationTriangle = faExclamationTriangle;
    faCheckCircle = faCheckCircle;
}
