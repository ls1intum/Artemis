import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';

import { ArTEMiSSharedModule, JhiWebsocketService } from '../../shared';
import { JhiAlertService } from 'ng-jhipster';
import { RepositoryService } from '../../entities/repository/repository.service';
import { HomeComponent } from '../../home';
import { JhiMainComponent } from '../../layouts';
import { QuizComponent } from '../participate/quiz.component';
import { EditMultipleChoiceQuestionComponent, EditDragAndDropQuestionComponent } from '.';
import { AngularFittextModule } from 'angular-fittext';
import { DndModule } from 'ng2-dnd';

@NgModule({
    imports: [
        ArTEMiSSharedModule,
        DndModule.forRoot(),
        AngularFittextModule
    ],
    declarations: [
        EditMultipleChoiceQuestionComponent,
        EditDragAndDropQuestionComponent
    ],
    entryComponents: [
        HomeComponent,
        QuizComponent,
        JhiMainComponent,
        EditMultipleChoiceQuestionComponent,
        EditDragAndDropQuestionComponent
    ],
    providers: [
        RepositoryService,
        JhiWebsocketService,
        JhiAlertService,
    ],
    exports: [
        EditMultipleChoiceQuestionComponent,
        EditDragAndDropQuestionComponent
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class ArTEMiSQuizEditModule {}
