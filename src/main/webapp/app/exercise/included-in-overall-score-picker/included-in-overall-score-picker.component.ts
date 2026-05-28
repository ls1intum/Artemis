import { Component, Input, input, output } from '@angular/core';
import { IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { NgClass } from '@angular/common';

@Component({
    selector: 'jhi-included-in-overall-score-picker',
    templateUrl: './included-in-overall-score-picker.component.html',
    imports: [TranslateDirective, NgClass],
})
export class IncludedInOverallScorePickerComponent {
    readonly IncludedInOverallScore = IncludedInOverallScore;

    // TODO: Skipped for migration because:
    //  Your application code writes to the input. This prevents migration.
    @Input()
    includedInOverallScore?: IncludedInOverallScore;
    // Option to disallow the NOT_INCLUDED option (used for exam exercises)
    readonly allowNotIncluded = input<boolean>(undefined!);
    readonly includedInOverallScoreChange = output();

    change(newValue: IncludedInOverallScore) {
        this.includedInOverallScore = newValue;
        this.includedInOverallScoreChange.emit(newValue);
    }
}
