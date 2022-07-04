import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Exam } from 'app/entities/exam.model';

@Component({
    selector: 'jhi-exam-mode-picker',
    templateUrl: './exam-mode-picker.component.html',
    styleUrls: ['./exam-mode-picker.component.scss'],
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
            this.examModeChanged.emit();
        }
    }
}
