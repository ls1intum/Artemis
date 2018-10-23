import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';

import { ArTEMiSSharedModule } from '../../shared';
import { JhiAlertService } from 'ng-jhipster';
import { RepositoryService } from '../../entities/repository/repository.service';
import { HomeComponent } from '../../home';
import { JhiMainComponent } from '../../layouts';
import { QuizComponent } from '../participate/quiz.component';
import { QuizExerciseComponent } from '../../entities/quiz-exercise';
import { AngularFittextModule } from 'angular-fittext';
import { AceEditorModule } from 'ng2-ace-editor';
import { DndModule } from 'ng2-dnd';
import { ArTEMiSQuizModule } from '../participate';
import { ArTEMiSQuizEditModule } from '../edit';
import { QuizReEvaluateComponent } from './quiz-re-evaluate.component';
import { ReEvaluateMultipleChoiceQuestionComponent } from './multiple-choice-question/re-evaluate-multiple-choice-question.component';
import { ReEvaluateDragAndDropQuestionComponent } from './drag-and-drop-question/re-evaluate-drag-and-drop-question.component';
import { QuizReEvaluateWarningComponent } from './quiz-re-evaluate-warning.component';
import { QuizReEvaluateService } from './quiz-re-evaluate.service';

@NgModule({
    imports: [ArTEMiSSharedModule, DndModule.forRoot(), AngularFittextModule, AceEditorModule, ArTEMiSQuizModule, ArTEMiSQuizEditModule],
    declarations: [
        QuizReEvaluateComponent,
        ReEvaluateMultipleChoiceQuestionComponent,
        ReEvaluateDragAndDropQuestionComponent,
        QuizReEvaluateWarningComponent
    ],
    entryComponents: [HomeComponent, QuizComponent, QuizExerciseComponent, JhiMainComponent, QuizReEvaluateWarningComponent],
    providers: [RepositoryService, JhiAlertService, QuizReEvaluateService],
    exports: [
        QuizReEvaluateComponent,
        ReEvaluateMultipleChoiceQuestionComponent,
        ReEvaluateDragAndDropQuestionComponent,
        QuizReEvaluateWarningComponent
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTEMiSQuizReEvaluateModule {}
