import { Component, EventEmitter, Input, Output } from '@angular/core';
import { IncludedInOverallScore } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-exam-included-in-overall-score-picker',
    templateUrl: './exam-included-in-overall-score-picker.component.html',
    styles: [],
})
export class ExamIncludedInOverallScorePickerComponent {
    readonly IncludedInOverallScore = IncludedInOverallScore;

    @Input()
    includedInOverallScore: IncludedInOverallScore | undefined;
    @Output()
    includedInOverallScoreChange = new EventEmitter();

    change(newValue: IncludedInOverallScore) {
        this.includedInOverallScore = newValue;
        this.includedInOverallScoreChange.emit(newValue);
    }
}
