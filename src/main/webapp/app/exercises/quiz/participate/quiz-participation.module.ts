import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { ArtemisQuizQuestionTypesModule } from 'app/exercises/quiz/shared/questions/artemis-quiz-question-types.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { QuizParticipationComponent } from './quiz-participation.component';
import { quizParticipationRoute } from './quiz-participation.route';

@NgModule({
    imports: [ArtemisSharedModule, RouterModule.forChild(quizParticipationRoute), ArtemisSharedComponentModule, ArtemisQuizQuestionTypesModule],
    declarations: [QuizParticipationComponent],
})
export class ArtemisQuizParticipationModule {}
