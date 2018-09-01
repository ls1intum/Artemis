import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';

import { ArTEMiSSharedModule, JhiWebsocketService } from '../../shared';
import { JhiAlertService } from 'ng-jhipster';
import { RepositoryService } from '../../entities/repository/repository.service';
import { HomeComponent } from '../../home';
import { JhiMainComponent } from '../../layouts';
import { QuizComponent } from '../participate/quiz.component';
import { QuizExerciseComponent } from '../../entities/quiz-exercise';
import { EditMultipleChoiceQuestionComponent } from './multiple-choice-question/edit-multiple-choice-question.component';
import { EditDragAndDropQuestionComponent } from './drag-and-drop-question/edit-drag-and-drop-question.component';
import { AngularFittextModule } from 'angular-fittext';
import { AceEditorModule } from 'ng2-ace-editor';
import { DndModule } from 'ng2-dnd';

@NgModule({
    imports: [
        ArTEMiSSharedModule,
        DndModule.forRoot(),
        AngularFittextModule,
        AceEditorModule
    ],
    declarations: [
        EditMultipleChoiceQuestionComponent,
        EditDragAndDropQuestionComponent
    ],
    entryComponents: [
        HomeComponent,
        QuizComponent,
        QuizExerciseComponent,
        JhiMainComponent,
        EditMultipleChoiceQuestionComponent,
        EditDragAndDropQuestionComponent
    ],
    providers: [
        RepositoryService,
        JhiWebsocketService,
        JhiAlertService
    ],
    exports: [
        EditMultipleChoiceQuestionComponent,
        EditDragAndDropQuestionComponent
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTEMiSQuizEditModule {}
