import { Component, computed, input } from '@angular/core';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

export type ExamModeBadgeSize = 'default' | 'large';

@Component({
    selector: 'jhi-exam-mode-badge',
    templateUrl: './exam-mode-badge.component.html',
    imports: [TranslateDirective],
})
export class ExamModeBadgeComponent {
    readonly exam = input.required<Pick<Exam, 'testExam' | 'testExamPracticeStartDate'>>();

    readonly size = input<ExamModeBadgeSize>('default');

    protected readonly translationKey = computed(() => {
        const exam = this.exam();
        if (exam.testExam === false) {
            return 'artemisApp.examManagement.testExam.realExam';
        }
        if (exam.testExamPracticeStartDate === undefined) {
            return 'artemisApp.examManagement.testExam.testExamPractice';
        }
        return 'artemisApp.examManagement.testExam.testExamSimulationAndPractice';
    });

    protected readonly isRealExam = computed(() => !this.exam().testExam);
    protected readonly isLarge = computed(() => this.size() === 'large');
}
