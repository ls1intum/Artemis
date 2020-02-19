import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { DeviceDetectorService } from 'ngx-device-detector';
import { quizParticipationRoute } from './quiz-participation.route';
import { QuizParticipationComponent } from './quiz-participation.component';
import { MultipleChoiceQuestionComponent } from './multiple-choice-question/multiple-choice-question.component';
import { DragAndDropQuestionComponent } from './drag-and-drop-question/drag-and-drop-question.component';
import { ShortAnswerQuestionComponent } from './short-answer-question/short-answer-question.component';
import { DragItemComponent } from './drag-and-drop-question/drag-item.component';
import { AngularFittextModule } from 'angular-fittext';
import { DndModule } from 'ng2-dnd';
import { QuizScoringInfoStudentModalComponent } from './quiz-scoring-infostudent-modal/quiz-scoring-info-student-modal.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';

const ENTITY_STATES = [...quizParticipationRoute];

@NgModule({
    imports: [ArtemisSharedModule, RouterModule.forChild(ENTITY_STATES), DndModule.forRoot(), AngularFittextModule, ArtemisSharedComponentModule],
    declarations: [
        QuizParticipationComponent,
        MultipleChoiceQuestionComponent,
        DragAndDropQuestionComponent,
        QuizScoringInfoStudentModalComponent,
        ShortAnswerQuestionComponent,
        DragItemComponent,
    ],
    providers: [DeviceDetectorService],
    exports: [MultipleChoiceQuestionComponent, DragAndDropQuestionComponent, ShortAnswerQuestionComponent, DragItemComponent],
})
export class ArtemisQuizParticipationModule {}
