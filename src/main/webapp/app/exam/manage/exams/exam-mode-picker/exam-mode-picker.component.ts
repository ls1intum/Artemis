import { Component, input, output } from '@angular/core';
import { Exam, ExamType, isTestExam } from 'app/exam/shared/entities/exam.model';
import { NgClass } from '@angular/common';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

@Component({
    selector: 'jhi-exam-mode-picker',
    templateUrl: './exam-mode-picker.component.html',
    styleUrls: ['./exam-mode-picker.component.scss'],
    imports: [NgClass, TranslateDirective],
})
export class ExamModePickerComponent {
    readonly exam = input.required<Exam>();
    readonly disableInput = input.required<boolean>();

    examModeChanged = output();

    protected isTestExam(): boolean {
        return isTestExam(this.exam());
    }

    /**
     * Set the mode and emit the changes to the parent component to notice changes
     * @param testExam
     */
    setExamMode(testExam: boolean) {
        if (!this.disableInput() && this.isTestExam() !== testExam) {
            this.exam().examType = testExam ? ExamType.PRACTICE : ExamType.REAL;
            this.exam().numberOfCorrectionRoundsInExam = testExam ? 0 : 1;
            this.examModeChanged.emit();
        }
    }
}
