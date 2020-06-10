import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { ExamParticipationComponent } from 'app/exam/participate/exam-participation.component';
import { examParticipationState } from 'app/exam/participate/exam-participation.route';

const ENTITY_STATES = [...examParticipationState];

@NgModule({
    imports: [RouterModule.forChild(ENTITY_STATES)],
    declarations: [ExamParticipationComponent],
})
export class ArtemisExamParticipationModule {}
