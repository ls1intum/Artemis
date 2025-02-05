import { NgModule } from '@angular/core';

import { IncludedInOverallScorePickerComponent } from 'app/exercises/shared/included-in-overall-score-picker/included-in-overall-score-picker.component';
import { FormsModule } from '@angular/forms';

@NgModule({
    imports: [FormsModule, IncludedInOverallScorePickerComponent],
    exports: [IncludedInOverallScorePickerComponent],
})
export class ArtemisIncludedInOverallScorePickerModule {}
