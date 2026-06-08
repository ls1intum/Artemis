import { Component, input } from '@angular/core';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { Tag } from 'primeng/tag';

/**
 * Status indicator for student exams
 * Number of student exams should match the number of registered users
 */
@Component({
    selector: 'jhi-student-exam-status',
    templateUrl: `./student-exam-status.component.html`,
    imports: [TranslateDirective, ArtemisTranslatePipe, Tag],
})
export class StudentExamStatusComponent {
    hasStudentsWithoutExam = input.required<boolean>();
    isTestExam = input.required<boolean>();
}
