import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ExamParticipationComponent } from 'app/exam/participate/exam-participation.component';
import { examParticipationState } from 'app/exam/participate/exam-participation.route';
import { ArtemisSharedModule } from 'app/shared/shared.module';

const ENTITY_STATES = [...examParticipationState];

@NgModule({
    imports: [RouterModule.forChild(ENTITY_STATES), ArtemisSharedModule],
    declarations: [ExamParticipationComponent],
})
export class ArtemisExamParticipationModule {}
