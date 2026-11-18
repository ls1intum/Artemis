import { Component, input, output } from '@angular/core';
import { TumAetUiButtonDirective, TumAetUiButtonGroupComponent, TumAetUiCheckboxComponent, TumAetUiTooltipDirective } from '@tumaet/ui-angular';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ExamMode } from 'app/exam/shared/entities/exam-mode.model';

/**
 * Lets instructors choose between an exam and a practice exam. A practice exam can optionally start with a supervised session
 * (stored as {@link ExamMode.TEST_WITH_SUPERVISED_SESSION}), which behaves like a real exam from the start date for the working time.
 */
@Component({
    selector: 'jhi-exam-mode-picker',
    templateUrl: './exam-mode-picker.component.html',
    imports: [TranslateDirective, ArtemisTranslatePipe, TumAetUiButtonDirective, TumAetUiButtonGroupComponent, TumAetUiCheckboxComponent, TumAetUiTooltipDirective],
})
export class ExamModePickerComponent {
    exam = input.required<Exam>();
    disableInput = input.required<boolean>();

    examModeChanged = output();

    protected readonly ExamMode = ExamMode;

    isRealExam(): boolean {
        return this.exam().examMode === ExamMode.REAL;
    }

    hasSupervisedSession(): boolean {
        return this.exam().examMode === ExamMode.TEST_WITH_SUPERVISED_SESSION;
    }

    /**
     * Switches to a practice exam. A previously chosen supervised session is kept when the practice exam is already selected.
     */
    selectPracticeExam() {
        if (this.isRealExam()) {
            this.setExamMode(ExamMode.TEST);
        }
    }

    /**
     * Adds or removes the supervised session at the beginning of a practice exam.
     * @param enabled whether the practice exam should start with a supervised session
     */
    setSupervisedSession(enabled: boolean) {
        this.setExamMode(enabled ? ExamMode.TEST_WITH_SUPERVISED_SESSION : ExamMode.TEST);
    }

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
