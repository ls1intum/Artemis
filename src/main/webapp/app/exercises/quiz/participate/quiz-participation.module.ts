import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';
import { quizParticipationRoute } from 'app/exercises/quiz/participate/quiz-participation.route';
import { QuizParticipationComponent } from 'app/exercises/quiz/participate/quiz-participation.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';

import { ArtemisQuizQuestionTypesModule } from 'app/exercises/quiz/shared/questions/artemis-quiz-question-types.module';

@NgModule({
    imports: [RouterModule.forChild(quizParticipationRoute), ArtemisSharedComponentModule, ArtemisQuizQuestionTypesModule, QuizParticipationComponent],
})
export class ArtemisQuizParticipationModule {}
