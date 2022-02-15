import { NgModule } from '@angular/core';
import { StudentExamWorkingTimeComponent } from 'app/exam/shared/student-exam-working-time.component';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';

@NgModule({
    imports: [ArtemisSharedCommonModule],
    declarations: [StudentExamWorkingTimeComponent],
    exports: [StudentExamWorkingTimeComponent],
})
export class ArtemisExamSharedModule {}
