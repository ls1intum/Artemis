import { NgModule } from '@angular/core';

import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ExamModePickerComponent } from 'app/exam/manage/exams/exam-mode-picker/exam-mode-picker.component';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [ExamModePickerComponent],
    exports: [ExamModePickerComponent],
})
export class ArtemisExamModePickerModule {}
