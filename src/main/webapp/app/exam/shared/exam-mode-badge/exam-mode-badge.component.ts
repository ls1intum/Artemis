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
    readonly exam = input.required<Exam>();

    readonly size = input<ExamModeBadgeSize>('default');

    protected readonly translationKey = computed(() => {
        const exam = this.exam();
        if (exam.testExam === false) {
            return 'artemisApp.examManagement.testExam.realExam';
        }
        if (exam.hasSimulation !== true) {
            return 'artemisApp.examManagement.testExam.testExam';
        }
        return 'artemisApp.examManagement.testExam.testExamWithSimulation';
    });

    protected readonly isRealExam = computed(() => !this.exam().testExam);
    protected readonly isLarge = computed(() => this.size() === 'large');
}
