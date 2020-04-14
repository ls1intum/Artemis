import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSidePanelModule } from 'app/shared/side-panel/side-panel.module';
import { ArtemisConfirmIconModule } from 'app/shared/confirm-icon/confirm-icon.module';
import { StudentQuestionsComponent } from 'app/overview/student-questions/student-questions.component';
import { StudentQuestionRowComponent } from 'app/overview/student-questions/student-question-row.component';
import {ArtemisMarkdownEditorModule} from "app/shared/markdown-editor/markdown-editor.module";



@NgModule({
    imports: [ArtemisSharedModule, ArtemisSidePanelModule, ArtemisConfirmIconModule, ArtemisMarkdownEditorModule],
    declarations: [StudentQuestionsComponent, StudentQuestionRowComponent],
    exports: [StudentQuestionsComponent],
})
export class ArtemisStudentQuestionsModule {}
