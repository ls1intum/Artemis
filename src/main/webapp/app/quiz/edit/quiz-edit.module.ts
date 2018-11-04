import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';

import { ArTEMiSSharedModule } from '../../shared';
import { JhiAlertService } from 'ng-jhipster';
import { RepositoryService } from '../../entities/repository/repository.service';
import { HomeComponent } from '../../home';
import { JhiMainComponent } from '../../layouts';
import { QuizComponent } from '../participate/quiz.component';
import { QuizExerciseComponent } from '../../entities/quiz-exercise';
import { EditMultipleChoiceQuestionComponent } from './multiple-choice-question/edit-multiple-choice-question.component';
import { EditDragAndDropQuestionComponent } from './drag-and-drop-question/edit-drag-and-drop-question.component';
import { EditShortAnswerQuestionComponent } from './short-answer-question/edit-short-answer-question.component';
import { AngularFittextModule } from 'angular-fittext';
import { AceEditorModule } from 'ng2-ace-editor';
import { DndModule } from 'ng2-dnd';
import { ArTEMiSQuizModule } from '../participate';

@NgModule({
    imports: [ArTEMiSSharedModule, DndModule.forRoot(), AngularFittextModule, AceEditorModule, ArTEMiSQuizModule],
    declarations: [EditMultipleChoiceQuestionComponent, EditDragAndDropQuestionComponent, EditShortAnswerQuestionComponent],
    entryComponents: [
        HomeComponent,
        QuizComponent,
        QuizExerciseComponent,
        JhiMainComponent,
        EditMultipleChoiceQuestionComponent,
        EditDragAndDropQuestionComponent,
        EditShortAnswerQuestionComponent
    ],
    providers: [RepositoryService, JhiAlertService],
    exports: [EditMultipleChoiceQuestionComponent, EditDragAndDropQuestionComponent, EditShortAnswerQuestionComponent],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTEMiSQuizEditModule {}
