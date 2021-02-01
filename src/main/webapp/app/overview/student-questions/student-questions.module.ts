import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSidePanelModule } from 'app/shared/side-panel/side-panel.module';
import { ArtemisConfirmIconModule } from 'app/shared/confirm-icon/confirm-icon.module';
import { StudentQuestionsComponent } from 'app/overview/student-questions/student-questions.component';
import { StudentQuestionRowComponent } from 'app/overview/student-questions/student-question-row/student-question-row.component';
import { StudentQuestionComponent } from 'app/overview/student-questions/student-question/student-question.component';
import { StudentQuestionAnswerComponent } from 'app/overview/student-questions/student-question-answer/student-question-answer.component';
import { StudentVotesComponent } from 'app/overview/student-questions/student-votes/student-votes.component';
import { RouterModule, Routes } from '@angular/router';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';

const routes: Routes = [
    {
        path: '',
        pathMatch: 'full',
        component: StudentQuestionsComponent,
    },
];

@NgModule({
    imports: [RouterModule.forChild(routes), ArtemisSharedModule, ArtemisSidePanelModule, ArtemisConfirmIconModule, ArtemisMarkdownModule, ArtemisMarkdownEditorModule],
    declarations: [StudentQuestionsComponent, StudentQuestionRowComponent, StudentQuestionComponent, StudentQuestionAnswerComponent, StudentVotesComponent],
    exports: [StudentQuestionsComponent],
})
export class ArtemisStudentQuestionsModule {}
