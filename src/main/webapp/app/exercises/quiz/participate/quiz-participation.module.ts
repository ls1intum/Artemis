import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { DeviceDetectorService } from 'ngx-device-detector';
import { quizParticipationRoute } from './quiz-participation.route';
import { QuizParticipationComponent } from './quiz-participation.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisQuizQuestionTypesModule } from 'app/exercises/quiz/shared/questions/artemis-quiz-question-types.module';

const ENTITY_STATES = [...quizParticipationRoute];

@NgModule({
    imports: [ArtemisSharedModule, RouterModule.forChild(ENTITY_STATES), ArtemisSharedComponentModule, ArtemisQuizQuestionTypesModule],
    providers: [DeviceDetectorService],
    declarations: [QuizParticipationComponent],
})
export class ArtemisQuizParticipationModule {}
