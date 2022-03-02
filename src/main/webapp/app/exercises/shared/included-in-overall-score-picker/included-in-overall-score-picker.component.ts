import { Component, EventEmitter, Input, Output } from '@angular/core';
import { IncludedInOverallScore } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-included-in-overall-score-picker',
    templateUrl: './included-in-overall-score-picker.component.html',
    styles: [],
})
export class IncludedInOverallScorePickerComponent {
    readonly IncludedInOverallScore = IncludedInOverallScore;

    @Input()
    includedInOverallScore: IncludedInOverallScore | undefined;
    @Input()
    excludeNotIncluded: boolean;
    @Output()
    includedInOverallScoreChange = new EventEmitter();

    change(newValue: IncludedInOverallScore) {
        this.includedInOverallScore = newValue;
        this.includedInOverallScoreChange.emit(newValue);
    }
}
