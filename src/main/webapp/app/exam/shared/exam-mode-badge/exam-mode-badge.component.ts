import { Component, computed, input } from '@angular/core';
import { ExamType } from 'app/exam/shared/entities/exam.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';

export type ExamModeBadgeSize = 'default' | 'large';

@Component({
    selector: 'jhi-exam-mode-badge',
    templateUrl: './exam-mode-badge.component.html',
    imports: [TranslateDirective],
})
export class ExamModeBadgeComponent {
    readonly examType = input<ExamType>();
    readonly size = input<ExamModeBadgeSize>('default');

    protected readonly translationKey = computed(() => {
        switch (this.examType()) {
            case ExamType.SIMULATION:
                return 'artemisApp.examManagement.testExam.testExamSimulation';
            case ExamType.PRACTICE:
                return 'artemisApp.examManagement.testExam.testExamPractice';
            case ExamType.SIMULATION_AND_PRACTICE:
                return 'artemisApp.examManagement.testExam.testExamSimulationAndPractice';
            default:
                return 'artemisApp.examManagement.testExam.realExam';
        }
    });

    protected readonly isRealExam = computed(() => this.examType() === ExamType.REAL || this.examType() === undefined);
    protected readonly isLarge = computed(() => this.size() === 'large');
}
