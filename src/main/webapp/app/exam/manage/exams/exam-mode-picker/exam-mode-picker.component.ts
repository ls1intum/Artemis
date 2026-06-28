import { Component, input, output } from '@angular/core';
import { Exam, ExamType } from 'app/exam/shared/entities/exam.model';
import { NgClass } from '@angular/common';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

@Component({
    selector: 'jhi-exam-mode-picker',
    templateUrl: './exam-mode-picker.component.html',
    styleUrls: ['./exam-mode-picker.component.scss'],
    imports: [NgClass, TranslateDirective],
})
export class ExamModePickerComponent {
    exam = input.required<Exam>();
    disableInput = input.required<boolean>();

    examModeChanged = output();

    protected readonly ExamType = ExamType;

    /**
     * Set the exam type directly and emit changes
     * @param examType
     */
    setExamType(examType: ExamType) {
        if (!this.disableInput() && this.exam().examType !== examType) {
            this.exam().examType = examType;
            this.exam().numberOfCorrectionRoundsInExam = examType !== ExamType.REAL ? 0 : 1;
            this.examModeChanged.emit();
        }
    }
}
