import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { JhiAlertService } from 'ng-jhipster';
import { DeviceDetectorService } from 'ngx-device-detector';

import { ArtemisSharedModule } from '../../shared';
import { quizRoute } from './quiz.route';
import { RepositoryService } from '../../entities/repository/repository.service';
import { HomeComponent } from '../../home';
import { JhiMainComponent } from '../../layouts';
import { QuizComponent } from './quiz.component';
import { MultipleChoiceQuestionComponent } from './multiple-choice-question/multiple-choice-question.component';
import { DragAndDropQuestionComponent } from './drag-and-drop-question/drag-and-drop-question.component';
import { ShortAnswerQuestionComponent } from './short-answer-question/short-answer-question.component';
import { DragItemComponent } from './drag-and-drop-question/drag-item.component';
import { AngularFittextModule } from 'angular-fittext';
import { DndModule } from 'ng2-dnd';
import { QuizScoringInfoStudentModalComponent } from './quiz-scoring-infostudent-modal/quiz-scoring-info-student-modal.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';

const ENTITY_STATES = [...quizRoute];

@NgModule({
    imports: [ArtemisSharedModule, RouterModule.forChild(ENTITY_STATES), DndModule.forRoot(), AngularFittextModule, ArtemisSharedComponentModule],
    declarations: [
        QuizComponent,
        MultipleChoiceQuestionComponent,
        DragAndDropQuestionComponent,
        QuizScoringInfoStudentModalComponent,
        ShortAnswerQuestionComponent,
        DragItemComponent,
    ],
    entryComponents: [HomeComponent, QuizComponent, JhiMainComponent],
    providers: [RepositoryService, JhiAlertService, DeviceDetectorService],
    exports: [MultipleChoiceQuestionComponent, DragAndDropQuestionComponent, ShortAnswerQuestionComponent, DragItemComponent],
})
export class ArtemisQuizModule {}
