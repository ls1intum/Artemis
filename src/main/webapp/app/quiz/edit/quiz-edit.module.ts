import { NgModule } from '@angular/core';
import { AlertService } from 'app/core/alert/alert.service';
import { EditMultipleChoiceQuestionComponent } from './multiple-choice-question/edit-multiple-choice-question.component';
import { EditDragAndDropQuestionComponent } from './drag-and-drop-question/edit-drag-and-drop-question.component';
import { EditShortAnswerQuestionComponent } from './short-answer-question/edit-short-answer-question.component';
import { AngularFittextModule } from 'angular-fittext';
import { AceEditorModule } from 'ng2-ace-editor';
import { ArtemisMarkdownEditorModule } from 'app/markdown-editor/markdown-editor.module';
import { DndModule } from 'ng2-dnd';
import { QuizScoringInfoModalComponent } from './quiz-scoring-info-modal/quiz-scoring-info-modal.component';
import { QuizExerciseComponent } from 'app/entities/quiz-exercise/quiz-exercise.component';
import { JhiMainComponent } from 'app/layouts/main/main.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { HomeComponent } from 'app/home/home.component';
import { ArtemisQuizModule } from 'app/quiz/participate/quiz.module';
import { QuizComponent } from 'app/quiz/participate/quiz.component';

@NgModule({
    imports: [ArtemisSharedModule, DndModule.forRoot(), AngularFittextModule, AceEditorModule, ArtemisQuizModule, ArtemisMarkdownEditorModule],
    declarations: [EditMultipleChoiceQuestionComponent, EditDragAndDropQuestionComponent, QuizScoringInfoModalComponent, EditShortAnswerQuestionComponent],
    entryComponents: [
        HomeComponent,
        QuizComponent,
        QuizExerciseComponent,
        JhiMainComponent,
        EditMultipleChoiceQuestionComponent,
        EditDragAndDropQuestionComponent,
        EditShortAnswerQuestionComponent,
    ],
    providers: [],
    exports: [EditMultipleChoiceQuestionComponent, EditDragAndDropQuestionComponent, EditShortAnswerQuestionComponent],
})
export class ArtemisQuizEditModule {}
