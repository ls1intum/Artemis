import { Component, computed, input } from '@angular/core';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { isRealExam } from 'app/exam/overview/exam.utils';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ExamMode } from 'app/exam/shared/entities/exam-mode.model';

export type ExamModeBadgeSize = 'default' | 'large';

@Component({
    selector: 'jhi-exam-mode-badge',
    templateUrl: './exam-mode-badge.component.html',
    imports: [TranslateDirective],
})
export class ExamModeBadgeComponent {
    readonly exam = input.required<Exam>();

    readonly size = input<ExamModeBadgeSize>('default');

    protected readonly translationKey = computed(() => {
        const exam = this.exam();
        if (isRealExam(exam)) {
            return 'artemisApp.examManagement.testExam.realExam';
        }
        if (exam.examMode !== ExamMode.TEST_WITH_SIMULATION) {
            return 'artemisApp.examManagement.testExam.testExam';
        }
        return 'artemisApp.examManagement.testExam.testExamWithSimulation';
    });

    protected readonly isRealExam = computed(() => isRealExam(this.exam()));
    protected readonly isLarge = computed(() => this.size() === 'large');
}
