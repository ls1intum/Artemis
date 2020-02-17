import { NgModule } from '@angular/core';
import { QuizComponent } from '../participate/quiz.component';
import { AngularFittextModule } from 'angular-fittext';
import { AceEditorModule } from 'ng2-ace-editor';
import { DndModule } from 'ng2-dnd';
import { QuizReEvaluateComponent } from './quiz-re-evaluate.component';
import { ReEvaluateMultipleChoiceQuestionComponent } from './multiple-choice-question/re-evaluate-multiple-choice-question.component';
import { ReEvaluateDragAndDropQuestionComponent } from './drag-and-drop-question/re-evaluate-drag-and-drop-question.component';
import { ReEvaluateShortAnswerQuestionComponent } from './short-answer-question/re-evaluate-short-answer-question.component';
import { QuizReEvaluateWarningComponent } from './quiz-re-evaluate-warning.component';
import { QuizReEvaluateService } from './quiz-re-evaluate.service';
import { ArtemisQuizEditModule } from 'app/quiz/edit/quiz-edit.module';
import { QuizExerciseComponent } from 'app/entities/quiz-exercise/quiz-exercise.component';
import { JhiMainComponent } from 'app/layouts/main/main.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { HomeComponent } from 'app/home/home.component';
import { ArtemisQuizModule } from 'app/quiz/participate/quiz.module';

@NgModule({
    imports: [ArtemisSharedModule, DndModule.forRoot(), AngularFittextModule, AceEditorModule, ArtemisQuizModule, ArtemisQuizEditModule],
    declarations: [
        QuizReEvaluateComponent,
        ReEvaluateMultipleChoiceQuestionComponent,
        ReEvaluateDragAndDropQuestionComponent,
        ReEvaluateShortAnswerQuestionComponent,
        QuizReEvaluateWarningComponent,
    ],
    entryComponents: [HomeComponent, QuizComponent, QuizExerciseComponent, JhiMainComponent, QuizReEvaluateWarningComponent],
    providers: [QuizReEvaluateService],
    exports: [
        QuizReEvaluateComponent,
        ReEvaluateMultipleChoiceQuestionComponent,
        ReEvaluateDragAndDropQuestionComponent,
        ReEvaluateShortAnswerQuestionComponent,
        QuizReEvaluateWarningComponent,
    ],
})
export class ArtemisQuizReEvaluateModule {}
