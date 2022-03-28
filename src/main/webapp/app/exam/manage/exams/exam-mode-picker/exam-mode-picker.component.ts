import { Component, EventEmitter, Input, Output } from '@angular/core';

@Component({
    selector: 'jhi-exam-mode-picker',
    templateUrl: './exam-mode-picker.component.html',
    styles: ['.btn.disabled { pointer-events: none }', '.btn-group.disabled { cursor: not-allowed; }'],
})
export class ExamModePickerComponent {
    @Input() isTestExam: Boolean;
    @Input() disabled: boolean;

    @Output() ngModelChange = new EventEmitter();

    /**
     * Set the mode and emit the changes to the parent component to notice changes
     * @param newValue
     */
    setExamMode(newValue: any) {
        if (!this.disabled) {
            this.isTestExam = newValue;
            this.ngModelChange.emit(newValue);
        }
    }
}
