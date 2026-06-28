import { Component, input, output } from '@angular/core';
import { Exam, ExamMode } from 'app/exam/shared/entities/exam.model';
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

    protected readonly ExamMode = ExamMode;

    /**
     * Set the exam mode directly and emit changes
     * @param examMode
     */
    setExamMode(examMode: ExamMode) {
        if (!this.disableInput() && this.exam().examMode !== examMode) {
            this.exam().examMode = examMode;
            this.exam().numberOfCorrectionRoundsInExam = examMode !== ExamMode.REAL ? 0 : 1;
            this.examModeChanged.emit();
        }
    }
}
