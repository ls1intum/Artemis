import { NgModule } from '@angular/core';

import { ExamModePickerComponent } from 'app/exam/manage/exams/exam-mode-picker/exam-mode-picker.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@NgModule({
    imports: [ArtemisSharedModule],
    declarations: [ExamModePickerComponent],
    exports: [ExamModePickerComponent],
})
export class ArtemisExamModePickerModule {}
