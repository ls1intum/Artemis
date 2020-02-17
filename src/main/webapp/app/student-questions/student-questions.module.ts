import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSidePanelModule } from 'app/components/side-panel/side-panel.module';
import { ArtemisConfirmIconModule } from 'app/components/confirm-icon/confirm-icon.module';
import { StudentQuestionsComponent } from 'app/student-questions/student-questions.component';
import { StudentQuestionRowComponent } from 'app/student-questions/student-question-row.component';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisSidePanelModule, ArtemisConfirmIconModule],
    declarations: [StudentQuestionsComponent, StudentQuestionRowComponent],
    exports: [StudentQuestionsComponent],
})
export class ArtemisStudentQuestionsModule {}
