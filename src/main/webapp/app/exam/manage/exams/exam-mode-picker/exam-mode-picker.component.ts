import { Component, input, output } from '@angular/core';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { NgClass } from '@angular/common';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { Tooltip } from 'primeng/tooltip';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ExamMode } from 'app/exam/shared/entities/exam-mode.model';

@Component({
    selector: 'jhi-exam-mode-picker',
    templateUrl: './exam-mode-picker.component.html',
    styleUrls: ['./exam-mode-picker.component.scss'],
    imports: [NgClass, TranslateDirective, Tooltip, ArtemisTranslatePipe],
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
            this.exam().numberOfCorrectionRoundsInExam = examMode === ExamMode.REAL ? 1 : 0;
            this.examModeChanged.emit();
        }
    }
}
