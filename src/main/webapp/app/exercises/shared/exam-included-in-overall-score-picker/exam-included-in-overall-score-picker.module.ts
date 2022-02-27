import { NgModule } from '@angular/core';

import { ArtemisSharedModule } from 'app/shared/shared.module';
import { FormsModule } from '@angular/forms';
import { ExamIncludedInOverallScorePickerComponent } from 'app/exercises/shared/exam-included-in-overall-score-picker/exam-included-in-overall-score-picker.component';

@NgModule({
    imports: [ArtemisSharedModule, FormsModule],
    declarations: [ExamIncludedInOverallScorePickerComponent],
    exports: [ExamIncludedInOverallScorePickerComponent],
})
export class ArtemisExamIncludedInOverallScorePickerModule {}
