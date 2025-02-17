import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Exam } from 'app/entities/exam/exam.model';
import { NgClass } from '@angular/common';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-exam-mode-picker',
    templateUrl: './exam-mode-picker.component.html',
    styleUrls: ['./exam-mode-picker.component.scss'],
    imports: [NgClass, TranslateDirective],
})
export class ExamModePickerComponent {
    @Input() exam: Exam;
    @Input() disableInput: boolean;

    @Output() examModeChanged = new EventEmitter();

    /**
     * Set the mode and emit the changes to the parent component to notice changes
     * @param testExam
     */
    setExamMode(testExam: boolean) {
        if (!this.disableInput && this.exam.testExam !== testExam) {
            this.exam.testExam = testExam;
            this.exam.numberOfCorrectionRoundsInExam = testExam ? 0 : 1;
            this.examModeChanged.emit();
        }
    }
}
