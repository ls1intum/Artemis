import { NgModule } from '@angular/core';

import { ArtemisSharedModule } from 'app/shared/shared.module';
import { IncludedInOverallScorePickerComponent } from 'app/exercises/shared/included-in-overall-score-picker/included-in-overall-score-picker.component';
import { FormsModule } from '@angular/forms';

@NgModule({
    imports: [ArtemisSharedModule, FormsModule],
    declarations: [IncludedInOverallScorePickerComponent],
    exports: [IncludedInOverallScorePickerComponent],
})
export class ArtemisIncludedInOverallScorePickerModule {}
