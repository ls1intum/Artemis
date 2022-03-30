import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Exam } from 'app/entities/exam.model';

@Component({
    selector: 'jhi-exam-mode-picker',
    templateUrl: './exam-mode-picker.component.html',
    styles: ['.btn.disabled { pointer-events: none }', '.btn-group.disabled { cursor: not-allowed; }'],
})
export class ExamModePickerComponent {
    @Input() exam: Exam;
    @Input() disabled: boolean;

    @Output() ngModelChange = new EventEmitter();

    /**
     * Set the mode and emit the changes to the parent component to notice changes
     * @param newValue
     */
    setExamMode(newValue: boolean) {
        if (!this.disabled && this.exam.testExam !== newValue) {
            this.exam.testExam = newValue;
            this.ngModelChange.emit();
        }
    }
}
