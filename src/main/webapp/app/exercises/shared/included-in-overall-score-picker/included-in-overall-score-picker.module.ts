import { NgModule } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { IncludedInOverallScorePickerComponent } from 'app/exercises/shared/included-in-overall-score-picker/included-in-overall-score-picker.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisSharedModule, FormsModule],
    declarations: [IncludedInOverallScorePickerComponent],
    exports: [IncludedInOverallScorePickerComponent],
})
export class ArtemisIncludedInOverallScorePickerModule {}
