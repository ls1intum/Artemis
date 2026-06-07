import { Component, input, output } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

@Component({
    selector: 'jhi-included-in-overall-score-picker',
    templateUrl: './included-in-overall-score-picker.component.html',
    imports: [TranslateDirective, ButtonModule],
})
export class IncludedInOverallScorePickerComponent {
    readonly IncludedInOverallScore = IncludedInOverallScore;

    readonly includedInOverallScore = input<IncludedInOverallScore>();
    readonly includedInOverallScoreChange = output<IncludedInOverallScore>();
    // Option to disallow the NOT_INCLUDED option (used for exam exercises)
    readonly allowNotIncluded = input<boolean>(false);

    change(newValue: IncludedInOverallScore) {
        this.includedInOverallScoreChange.emit(newValue);
    }
}
