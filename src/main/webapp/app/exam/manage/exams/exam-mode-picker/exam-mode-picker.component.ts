import { Component, input, output } from '@angular/core';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { NgClass } from '@angular/common';
import { TranslateDirective } from 'app/shared/language/translate.directive';

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

    /**
     * Set the mode and emit the changes to the parent component to notice changes
     * @param testExam
     */
    setExamMode(testExam: boolean) {
        if (!this.disableInput() && this.exam().testExam !== testExam) {
            this.exam().testExam = testExam;
            this.exam().numberOfCorrectionRoundsInExam = testExam ? 0 : 1;
            this.examModeChanged.emit();
        }
    }
}
